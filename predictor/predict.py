"""
Text classification with the torchtext library
==================================

This training code is extended from pytorch text classification tutorial: https://pytorch.org/tutorials/beginner/text_sentiment_ngrams_tutorial.html

By using this sample code, I will show you how a mode training code can be integrated with out mini autoML deep learning framework:

   - Fetch training data from dataset-management (DM) data bucket
   - Convert training data from DM format to Pytorch dataset format
   - Build data processing pipeline to convert the raw text strings into ``torch.Tensor`` that can be used to train the model
   - Shuffle and iterate the data with `torch.utils.data.DataLoader`
   - Training model
   - Push the model, checkpoints and training metrics to metadata store
"""

import json
import os
from concurrent import futures
from os.path import exists

import grpc
import torch
from grpc_health.v1 import health_pb2_grpc, health
from torch import nn
from torchtext.data.utils import get_tokenizer

import prediction_service_pb2_grpc, prediction_service_pb2
from utils import PredictorConfig


######################################################################
# Define the model architecture
# ----------------
class TextClassificationModel(nn.Module):
    def __init__(self, vocab_size, embed_dim, fc_size, num_class):
        super(TextClassificationModel, self).__init__()
        self.embedding = nn.EmbeddingBag(vocab_size, embed_dim, sparse=True)
        self.fc1 = nn.Linear(embed_dim, fc_size)
        self.fc2 = nn.Linear(fc_size, num_class)
        self.init_weights()

    def init_weights(self):
        initrange = 0.5
        self.embedding.weight.data.uniform_(-initrange, initrange)
        self.fc1.weight.data.uniform_(-initrange, initrange)
        self.fc1.bias.data.zero_()
        self.fc2.weight.data.uniform_(-initrange, initrange)
        self.fc2.bias.data.zero_()

    def forward(self, text, offsets):
        embedded = self.embedding(text, offsets)
        return self.fc2(self.fc1(embedded))


######################################################################
# Define model serving logic
# Model manager will keep all loaded models in memory
# ----------------
class ModelManager:
    def __init__(self, config, tokenizer, device):
        self.model_dir = config.MODEL_DIR
        self.models = {}
        self.config = config
        self.tokenizer = tokenizer
        self.device = device

    def model_key(self, model_id):
        return model_id + "_model"

    def model_vocab_key(self, model_id):
        return model_id + "_vocab"

    def model_classes(self, model_id):
        return model_id + "_classes"

    def load_model(self, model_id):
        if model_id in self.models:
            return

        # load model files, including vocabulary, prediction class mapping.
        vacab_path = os.path.join(self.model_dir, model_id, "vocab.pth")
        manifest_path = os.path.join(self.model_dir, model_id, "manifest.json")
        model_path = os.path.join(self.model_dir, model_id, "model.pth")

        if not exists(vacab_path):
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, model_id + ", model vocabulary not found")
        if not exists(manifest_path):
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, ", model class not found")
        if not exists(model_path):
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, ", model file not found")

        vocab = torch.load(vacab_path)
        with open(manifest_path, 'r') as f:
            manifest = json.loads(f.read())
        classes = manifest['classes']

        # initialize model and load model weights
        num_class, vocab_size, emsize = len(classes), len(vocab), 64
        model = TextClassificationModel(vocab_size, emsize, self.config.FC_SIZE, num_class).to(self.device)
        model.load_state_dict(torch.load(model_path))
        model.eval()

        self.models[self.model_key(model_id)] = model
        self.models[self.model_vocab_key(model_id)] = vocab
        self.models[self.model_classes(model_id)] = classes

    def predict(self, model_id, document):
        if self.model_key(model_id) not in self.models:
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, model_id + ", model not found")

        if self.model_vocab_key(model_id) not in self.models:
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, model_id + ", model vocabulary not found")

        if self.model_classes(model_id) not in self.models:
            raise ModelLoadingError(grpc.StatusCode.NOT_FOUND, model_id + ", model classes not found")

        model = self.models[self.model_key(model_id)]
        vocab = self.models[self.model_vocab_key(model_id)]
        classes = self.models[self.model_classes(model_id)]

        def text_pipeline(x):
            return vocab(self.tokenizer(x))

        processed_text = torch.tensor(text_pipeline(document), dtype=torch.int64)
        offsets = [0, processed_text.size(0)]
        offsets = torch.tensor(offsets[:-1]).cumsum(dim=0)
        val = model(processed_text, offsets)

        res_index = val.argmax(1).item()
        res = classes[str(res_index)]
        print("label is {}, {}".format(res_index, res))
        return res


class ModelLoadingError(Exception):
    def __init__(self, status_code, message):
        self.status_code = status_code
        self.message = message


class PredictorServicer(prediction_service_pb2_grpc.PredictorServicer):
    def __init__(self, model_manager):
        self.model_manager = model_manager

    def PredictorPredict(self, request, context: grpc.ServicerContext):
        try:
            self.model_manager.load_model(model_id=request.runId)
            class_name = self.model_manager.predict(request.runId, request.document)
            return prediction_service_pb2.PredictorPredictResponse(response=json.dumps({'result': class_name}))
        except ModelLoadingError as ex:
            context.abort(ex.status_code, ex.message)


def serve():
    config = PredictorConfig()
    print("Predictor parameters")
    print(str(config))
    model_manager = ModelManager(config, tokenizer=get_tokenizer('basic_english'), device="cpu")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    prediction_service_pb2_grpc.add_PredictorServicer_to_server(
        PredictorServicer(model_manager), server)
    health_servicer = health.HealthServicer(
        experimental_non_blocking=True,
        experimental_thread_pool=futures.ThreadPoolExecutor(max_workers=10))
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    server.add_insecure_port('0.0.0.0:51001')
    server.start()
    server.wait_for_termination()


######################################################################
# Start the inference serve
# ----------------

if __name__ == '__main__':
    serve()
