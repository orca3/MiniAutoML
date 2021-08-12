"""
Text classification with the torchtext library
==================================

This training code is extended from pytorch text classification tutorial: https://pytorch.org/tutorials/beginner/text_sentiment_ngrams_tutorial.html

By using this sample code, I will show how a mode training code can be integrated with a deep learning framework:

   - Fetch training data from dataset-management (DM) data bucket
   - Convert training data from DM format to Pytorch dataset format
   - Build data processing pipeline to convert the raw text strings into ``torch.Tensor`` that can be used to train the model
   - Shuffle and iterate the data with `torch.utils.data.DataLoader`
   - Training model
   - Push the model, checkpoints and training metrics to metadata store
"""

import csv
import io
import time
import torch
from torchtext.data.utils import get_tokenizer
from torchtext.utils import unicode_csv_reader
from torchtext.vocab import build_vocab_from_iterator

from torch import nn

from torchtext.data.datasets_utils import (
    _RawTextIterableDataset,
    _wrap_split_argument,
    _add_docstring_header,
    _create_dataset_directory,
    _create_data_from_csv,
)

from torch.utils.data import DataLoader
from torch.utils.data.dataset import random_split
from torchtext.data.functional import to_map_style_dataset

from minio import Minio

######################################################################
# Define parameters and set values from environment variables
# ---------------------

# <TODO, read with default value when converting to docker image>
# define hyperparameter
EPOCHS = 20 # epoch
LR = 5  # learning rate
BATCH_SIZE = 64 # batch size for training
FC_SIZE = 128 # node count for the middle fully connected layer
MINIO_SERVER = "127.0.0.1:9000"
MINIO_SERVER_ACCESS_KEY = "foooo"
MINIO_SERVER_SECRET_KEY = "barbarbar"
TRAINING_DATA_BUCKET = "mini-automl-dm"
TRAINING_DATA_PATH = "versionedDatasets/1/hashBA==/"
MODEL_BUCKET = "mini-automl-serving"
MODEL_ID = "aaf98dsfase"
MODEL_VERSION = "1"


######################################################################
# Download training data from MinIO
# ---------------------

client = Minio(
    MINIO_SERVER,
    access_key=MINIO_SERVER_ACCESS_KEY,
    secret_key=MINIO_SERVER_SECRET_KEY,
    secure=False
)

# download training data
client.fget_object(TRAINING_DATA_BUCKET, TRAINING_DATA_PATH + "examples.csv", "examples.csv")
client.fget_object(TRAINING_DATA_BUCKET, TRAINING_DATA_PATH + "labels.csv", "labels.csv")

######################################################################
# Convert data from dataset management training data format to Pytorch dataset
# ---------------------

def create_data_from_csv(data_path):
    with io.open(data_path, encoding="utf8") as f:
        reader = unicode_csv_reader(f)
        for row in reader:
            # set label as first column
            yield int(row[1]), ' '.join(row[0])

def get_dataset_iter(datasetname, path):
    with open(path, mode='r') as infile:
        reader = csv.reader(infile)
        lines = len(list(reader))
    return _RawTextIterableDataset("",  lines, create_data_from_csv(path)), lines

def load_label_dict(path):
    with open(path, mode='r') as infile:
        reader = csv.reader(infile)
        label_dict = {int(rows[0]): rows[1] for rows in reader}
        return label_dict

######################################################################
# Generate data batch and iterator 
# --------------------------------

tokenizer = get_tokenizer('basic_english')

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def yield_tokens(data_iter):
    for _, text in data_iter:
        yield tokenizer(text)

example_iter, lines = get_dataset_iter("intent-dm", "examples.csv")
vocab = build_vocab_from_iterator(yield_tokens(example_iter), specials=["<unk>"])
vocab.set_default_index(vocab["<unk>"])

text_pipeline = lambda x: vocab(tokenizer(x))
label_pipeline = lambda x: int(x) - 1

def collate_batch(batch):
    label_list, text_list, offsets = [], [], [0]
    for (_label, _text) in batch:
         label_list.append(label_pipeline(_label))
         processed_text = torch.tensor(text_pipeline(_text), dtype=torch.int64)
         text_list.append(processed_text)
         offsets.append(processed_text.size(0))
    label_list = torch.tensor(label_list, dtype=torch.int64)
    offsets = torch.tensor(offsets[:-1]).cumsum(dim=0)
    text_list = torch.cat(text_list)
    return label_list.to(device), text_list.to(device), offsets.to(device)

######################################################################
# Define the model
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

example_iter, lines = get_dataset_iter("intent-dm", "examples.csv")
num_class = len(set([label for (label, text) in example_iter]))
vocab_size = len(vocab)
emsize = 64
model = TextClassificationModel(vocab_size, emsize, FC_SIZE, num_class).to(device)

######################################################################
# Define functions to train the model and evaluate results.
# ---------------------------------------------------------
#

def train(dataloader):
    model.train()
    total_acc, total_count = 0, 0
    log_interval = 500
    start_time = time.time()

    for idx, (label, text, offsets) in enumerate(dataloader):
        optimizer.zero_grad()
        predicted_label = model(text, offsets)
        loss = criterion(predicted_label, label)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 0.1)
        optimizer.step()
        total_acc += (predicted_label.argmax(1) == label).sum().item()
        total_count += label.size(0)
        if idx % log_interval == 0 and idx > 0:
            elapsed = time.time() - start_time
            print('| epoch {:3d} | {:5d}/{:5d} batches '
                  '| accuracy {:8.3f}'.format(epoch, idx, len(dataloader),
                                              total_acc/total_count))
            total_acc, total_count = 0, 0
            start_time = time.time()

def evaluate(dataloader):
    model.eval()
    total_acc, total_count = 0, 0

    with torch.no_grad():
        for idx, (label, text, offsets) in enumerate(dataloader):
            predicted_label = model(text, offsets)
            loss = criterion(predicted_label, label)
            total_acc += (predicted_label.argmax(1) == label).sum().item()
            total_count += label.size(0)
    return total_acc/total_count
  
######################################################################
# Split the dataset and do the model training
# -----------------------------------

# load examples
example_iter, lines = get_dataset_iter("intent-dm", "examples.csv")
# load labels
labels = load_label_dict("labels.csv")

# split dataset
test_ratio = 0.1
valid_ratio = 0.95

num_test = int(lines * test_ratio)
num_train = int((lines - num_test) * valid_ratio)
num_valid = lines - num_test - num_train

total_dataset = to_map_style_dataset(example_iter)
split_train_, split_valid_, split_test_ = \
    random_split(total_dataset, [num_train, num_valid, num_test])

criterion = torch.nn.CrossEntropyLoss()
optimizer = torch.optim.SGD(model.parameters(), lr=LR)
scheduler = torch.optim.lr_scheduler.StepLR(optimizer, 1.0, gamma=0.1)
total_accu = None

train_dataloader = DataLoader(split_train_, batch_size=BATCH_SIZE,
                              shuffle=True, collate_fn=collate_batch)
valid_dataloader = DataLoader(split_valid_, batch_size=BATCH_SIZE,
                              shuffle=True, collate_fn=collate_batch)
test_dataloader = DataLoader(split_test_, batch_size=BATCH_SIZE,
                             shuffle=True, collate_fn=collate_batch)

for epoch in range(1, EPOCHS + 1):
    epoch_start_time = time.time()
    train(train_dataloader)
    accu_val = evaluate(valid_dataloader)
    if total_accu is not None and total_accu > accu_val:
      scheduler.step()
    else:
       total_accu = accu_val
    print('-' * 59)
    print('| end of epoch {:3d} | time: {:5.2f}s | '
          'valid accuracy {:8.3f} '.format(epoch,
                                           time.time() - epoch_start_time,
                                           accu_val))
    print('-' * 59)

print('Checking the results of test dataset.')
accu_test = evaluate(test_dataloader)
print('test accuracy {:8.3f}'.format(accu_test))