import json
import torch
import os
import logging

from torch import nn
from torchtext.data.utils import get_tokenizer
from ts.torch_handler.base_handler import BaseHandler

logger = logging.getLogger(__name__)

class ModelHandler(BaseHandler):
    """
    A custom model handler implementation for serving intent classification prediction 
    in torch serving server.
    """

    class TextClassificationModel(nn.Module):
        def __init__(self, vocab_size, embed_dim, fc_size, num_class):
            super(ModelHandler.TextClassificationModel, self).__init__()
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

    def __init__(self):
        self.context = None
        self.model = None 
        self.initialized = False
        self.fcsize = 128
        self.manifest = None
        self.tokenizer = get_tokenizer('basic_english')

    def initialize(self, ctx):
        """
        Initialize model. This will be called during model loading time
        :param context: Initial context contains model server system properties.
        :return:
        """

        self.context = ctx
        properties = ctx.system_properties
        model_dir = properties.get("model_dir")
        model_path = os.path.join(model_dir, "model.pth")
        vacab_path = os.path.join(model_dir, "vocab.pth")
        manifest_path = os.path.join(model_dir, "manifest.json")
        
        # load vocabulary
        self.vocab = torch.load(vacab_path)

        # load model manifest, including label index map.
        with open(manifest_path, 'r') as f:
            self.manifest = json.loads(f.read())
        classes = self.manifest['classes']

        num_class = len(classes)
        vocab_size = len(self.vocab)
        emsize = 64
        self.model = self.TextClassificationModel(vocab_size, emsize, self.fcsize, num_class).to("cpu")
        self.model.load_state_dict(torch.load(model_path))
        self.model.eval()

        logger.info('intent classification model file loaded successfully')
        self.initialized = True

    def preprocess(self, data):
        """
        Transform raw input into model input data.
        :param batch: list of raw requests, should match batch size
        :return: list of preprocessed model input data
        """
        # Take the input data and make it inference ready
        logger.info('data={}'.format(data))

        preprocessed_data = data[0].get("data")
        if preprocessed_data is None:
            preprocessed_data = data[0].get("body")

        text_pipeline = lambda x: self.vocab(self.tokenizer(x))
        
        user_input = " ".join(str(preprocessed_data))
        processed_text = torch.tensor(text_pipeline(user_input), dtype=torch.int64)
        offsets = [0, processed_text.size(0)]
        offsets = torch.tensor(offsets[:-1]).cumsum(dim=0)

        logger.info('UserInput={}; TensorInput={}; Offset={}'.format(user_input, processed_text, offsets))
        return (processed_text, offsets)

    def inference(self, model_input):
        """
        Internal inference methods
        :param model_input: transformed model input data
        :return: list of inference output in NDArray
        """
        # Do some inference call to engine here and return output
        model_output = self.model.forward(model_input[0], model_input[1])
        return model_output

    def postprocess(self, inference_output):
        """
        Return inference result.
        :param inference_output: list of inference output
        :return: list of predict results
        """
        # Take output from network and post-process to desired format
        res_index = inference_output.argmax(1).item()
        logger.info("return {}".format(res_index))
        classes = self.manifest['classes']
        postprocess_output = classes[str(res_index)]
        return [{"predict_res":postprocess_output}]

    def handle(self, data, context):
        """
        Invoke by TorchServe for prediction request.
        Do pre-processing of data, prediction using model and postprocessing of prediciton output
        :param data: Input data for prediction
        :param context: Initial context contains model server system properties.
        :return: prediction output
        """
        model_input = self.preprocess(data)
        model_output = self.inference(model_input)
        return self.postprocess(model_output)


## local test
# class Context:
#     system_properties={"model_dir":"/Users/chi.wang/workspace/cw/book/MiniAutoML/42"}

# class PredictPayload:
#     def get(self, str):
#         return "make a 10 minute timer"

# ctx = Context()
# handler = ModelHandler()
# handler.initialize(ctx)

# print("prediction={}".format(handler.handle([PredictPayload()], ctx)))

## torch serve package command
# torch-model-archiver --model-name intent_classification --version 1.0 --model-file torchserve_model.py --serialized-file /Users/chi.wang/workspace/cw/book/MiniAutoML/42/model.pth --handler torchserve_handler.py --extra-files /Users/chi.wang/workspace/cw/book/MiniAutoML/42/vocab.pth,/Users/chi.wang/workspace/cw/book/MiniAutoML/42/manifest.json
