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
import torch
import os
from os.path import exists
from flask import Flask, jsonify, request, abort
from torch import nn

from torchtext.data.utils import get_tokenizer
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
class ModelManager():
    def __init__(self, model_dir):
        self.model_dir = model_dir
        self.models = {}

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
            abort(404, description= model_id + ", model vocabulary not found")
        if not exists(manifest_path):
            abort(404, description= model_id + ", model class not found")
        if not exists(model_path):
            abort(404, description= model_id + ", model file not found")   

        vocab = torch.load(vacab_path)
        with open(manifest_path, 'r') as f:
            manifest = json.loads(f.read())
        classes = manifest['classes']
        
        # initialize model and load model weights
        num_class, vocab_size, emsize = len(classes), len(vocab), 64
        model = TextClassificationModel(vocab_size, emsize, config.FC_SIZE, num_class).to(device)
        model.load_state_dict(torch.load(model_path))
        model.eval()

        self.models[self.model_key(model_id)] = model
        self.models[self.model_vocab_key(model_id)] = vocab
        self.models[self.model_classes(model_id)] = classes

    def predict(self, model_id, document):
        if self.model_key(model_id) not in self.models:
            abort(404, description= model_id + ", model not found")

        if self.model_vocab_key(model_id) not in self.models:
            abort(404, description= model_id + ", model vocabulary not found")

        if self.model_classes(model_id) not in self.models:
            abort(404, description= model_id + ", model classes not found")    
        
        model = self.models[self.model_key(model_id)]
        vocab = self.models[self.model_vocab_key(model_id)]
        classes = self.models[self.model_classes(model_id)]

        text_pipeline = lambda x: vocab(tokenizer(x))

        user_input = " ".join(document.decode("utf-8"))
        processed_text = torch.tensor(text_pipeline(user_input), dtype=torch.int64)
        offsets = [0, processed_text.size(0)]
        offsets = torch.tensor(offsets[:-1]).cumsum(dim=0)
        val = model(processed_text, offsets)

        res_index = val.argmax(1).item()
        res = classes[str(res_index)]
        print("label is {}, {}".format(res_index, res))
        return res


######################################################################
# Start the inference serve
# ----------------
config = PredictorConfig()
print("Predictor parameters")
print(str(config))

tokenizer = get_tokenizer('basic_english')
device = "cpu"

app = Flask(__name__)
model_manager = ModelManager(config.MODEL_DIR)

@app.route('/predictions/<model_id>', methods=['GET'])
def predict(model_id):
    if request.method == 'GET':
        model_manager.load_model(model_id=model_id)
        class_name = model_manager.predict(model_id, request.get_data())
        return jsonify({'res': class_name})

@app.errorhandler(404)
def model_not_found(e):
    return jsonify(error=str(e)), 404

if __name__ == '__main__':
    app.run(host='0.0.0.0')


