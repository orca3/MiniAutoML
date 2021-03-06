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

import csv
import datetime
import io
import os
import time
import json

import torch
import torch.distributed as dist
import pathlib
from minio import Minio
from torch import nn
from torch.nn.parallel import DistributedDataParallel as DDP
from torch.utils.data import DataLoader
from torch.utils.data.dataset import random_split
from torch.utils.data.distributed import DistributedSampler
from torchtext.data.datasets_utils import (
    _RawTextIterableDataset,
)
from torchtext.data.utils import get_tokenizer
from torchtext.utils import unicode_csv_reader
from torchtext.vocab import build_vocab_from_iterator

from orca3_utils import Orca3Utils, TrainingConfig, TorchModelArchiver

class MapStyleDataset(torch.utils.data.Dataset):
    def __init__(self, iter_data):
        self._data = list(iter_data)

    def __len__(self):
        return len(self._data)

    def __getitem__(self, idx):
        return self._data[idx]


######################################################################
# Define parameters and read values from environment variables
# ---------------------
# Launch training with two process, first value is RANK, second is WORLD_SIZE
# train.py 0 2
# train.py 1 2
config = TrainingConfig()
algorithm_name = config.ALGORITHM_NAME
print("Training parameters")
print(str(config))


def should_distribute():
    return dist.is_available() and config.WORLD_SIZE > 1


def is_distributed():
    return dist.is_available() and dist.is_initialized()


orca3_utils = Orca3Utils(config.METADATA_STORE_SERVER, config.JOB_ID, config.RANK,
                         config.TRAINING_DATASET_ID, config.TRAINING_DATASET_VERSION_HASH, config.MODEL_VERSION,
                         config.MODEL_NAME)
model_archiver = TorchModelArchiver()

if config.RANK == 0:
    run_started = orca3_utils.log_run_start()
    config.MODEL_BUCKET = run_started.bucket
    config.MODEL_OBJECT_NAME = run_started.path

if should_distribute():
    print("Using distributed PyTorch with {0} backend, world size={1}, rank={2}".format("gloo", config.WORLD_SIZE,
                                                                                        config.RANK))
    dist.init_process_group("gloo", rank=config.RANK, world_size=config.WORLD_SIZE)

######################################################################
# Download training data from MinIO
# ---------------------

client = Minio(
    config.MINIO_SERVER,
    access_key=config.MINIO_SERVER_ACCESS_KEY,
    secret_key=config.MINIO_SERVER_SECRET_KEY,
    secure=False
)

# download training data
example_path = "{0}/{1}".format(config.RANK, "examples.csv")
label_path = "{0}/{1}".format(config.RANK, "labels.csv")

client.fget_object(config.TRAINING_DATA_BUCKET, os.path.join(config.TRAINING_DATA_PATH, "examples.csv"), example_path)
client.fget_object(config.TRAINING_DATA_BUCKET, os.path.join(config.TRAINING_DATA_PATH, "labels.csv"), label_path)


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
    return _RawTextIterableDataset("", lines, create_data_from_csv(path)), lines


def load_label_dict(path):
    with open(path, mode='r') as infile:
        reader = csv.reader(infile)
        label_dict = {int(rows[0]) - 1: rows[1] for rows in reader}
        return label_dict


######################################################################
# Generate data batch and iterator
# --------------------------------

tokenizer = get_tokenizer('basic_english')
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

def yield_tokens(data_iter):
    for _, text in data_iter:
        yield tokenizer(text)


example_iter, lines = get_dataset_iter("intent-dm", example_path)
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
        self.fc2 = nn.Linear(fc_size, fc_size)
        self.fc3 = nn.Linear(fc_size, num_class)
        self.init_weights()

    def init_weights(self):
        initrange = 0.5
        self.embedding.weight.data.uniform_(-initrange, initrange)
        self.fc1.weight.data.uniform_(-initrange, initrange)
        self.fc1.bias.data.zero_()
        self.fc2.weight.data.uniform_(-initrange, initrange)
        self.fc2.bias.data.zero_()
        self.fc3.weight.data.uniform_(-initrange, initrange)
        self.fc3.bias.data.zero_()

    def forward(self, text, offsets):
        embedded = self.embedding(text, offsets)
        return self.fc3(self.fc2(self.fc1(embedded)))


example_iter, lines = get_dataset_iter("intent-dm", example_path)
num_class = len(set([label for (label, text) in example_iter]))
vocab_size = len(vocab)
emsize = 64
model = TextClassificationModel(vocab_size, emsize, config.FC_SIZE, num_class).to(device)
if is_distributed():
    model = DDP(model)


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
                                              total_acc / total_count))
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
    return total_acc / total_count


######################################################################
# Split the dataset and do the model training
# -----------------------------------

# load examples
example_iter, lines = get_dataset_iter("intent-dm", example_path)
# load labels
labels = load_label_dict(label_path)

# split dataset
test_ratio = 0.1
valid_ratio = 0.95

num_test = int(lines * test_ratio)
num_train = int((lines - num_test) * valid_ratio)
num_valid = lines - num_test - num_train

total_dataset = MapStyleDataset(example_iter)
split_train_, split_valid_, split_test_ = \
    random_split(total_dataset, [num_train, num_valid, num_test])

criterion = torch.nn.CrossEntropyLoss()
optimizer = torch.optim.SGD(model.parameters(), lr=config.LR)
scheduler = torch.optim.lr_scheduler.StepLR(optimizer, 1.0, gamma=0.1)
total_accu = None

if is_distributed():
    # restricts data loading to a subset of the dataset exclusive to the current process
    train_sampler = DistributedSampler(dataset=split_train_, num_replicas=config.WORLD_SIZE, rank=config.RANK)
    # training data for this process will from its own (RANK) partition.
    train_dataloader = DataLoader(dataset=split_train_, batch_size=config.BATCH_SIZE,
                                  shuffle=False, collate_fn=collate_batch, sampler=train_sampler,
                                  num_workers=0)
    # test and validation loader doesn't have to follow the distributed sampling strategy.
    valid_dataloader = DataLoader(split_valid_, batch_size=config.BATCH_SIZE,
                                  shuffle=True, collate_fn=collate_batch)
    test_dataloader = DataLoader(split_test_, batch_size=config.BATCH_SIZE,
                                 shuffle=True, collate_fn=collate_batch)
else:
    train_dataloader = DataLoader(split_train_, batch_size=config.BATCH_SIZE,
                                  shuffle=True, collate_fn=collate_batch)
    valid_dataloader = DataLoader(split_valid_, batch_size=config.BATCH_SIZE,
                                  shuffle=True, collate_fn=collate_batch)
    test_dataloader = DataLoader(split_test_, batch_size=config.BATCH_SIZE,
                                 shuffle=True, collate_fn=collate_batch)

for epoch in range(1, config.EPOCHS + 1):
    epoch_start_time = datetime.datetime.now()
    train(train_dataloader)
    accu_val = evaluate(valid_dataloader)
    if total_accu is not None and total_accu > accu_val:
        scheduler.step()
    else:
        total_accu = accu_val
    print('-' * 59)
    print('| end of epoch {:3d} | time: {:5.2f}s | '
          'valid accuracy {:8.3f} '.format(epoch,
                                           (datetime.datetime.now() - epoch_start_time).seconds,
                                           accu_val))
    print('-' * 59)
    metrics = {
        "accuracy": str(accu_val)
    }
    orca3_utils.log_epoch(epoch_start_time.isoformat(), epoch, metrics)

if config.RANK == 0:
    print('Checking the results of test dataset.')
    accu_test = evaluate(test_dataloader)
    print('test accuracy {:8.3f}'.format(accu_test))

    # Print model's state_dict
    print("Model's state_dict:")
    for param_tensor in model.state_dict():
        print(param_tensor, "\t", model.state_dict()[param_tensor].size())

    # Print optimizer's state_dict
    print("Optimizer's state_dict:")
    for var_name in optimizer.state_dict():
        print(var_name, "\t", optimizer.state_dict()[var_name])

    # save model weights and bias
    if not os.path.exists(config.JOB_ID):
        os.makedirs(config.JOB_ID)
    model_local_path = os.path.join(config.JOB_ID, "model.pth")
    torch.save(model.state_dict(), model_local_path)

    # save manifest file
    # TODO: save manifest (except classes) as metadata in metastore.
    model_manifest_path = os.path.join(config.JOB_ID, 'manifest.json')
    modelManifest = {
        "Algorithm": "intent-classification",
        "Framework": "Pytorch",
        "FrameworkVersion": "1.9.0",
        "ModelName": config.MODEL_NAME,
        "CodeVersion": config.MODEL_VERSION,
        "ModelVersion": config.MODEL_SERVING_VERSION,
        "classes": labels,
        "fc_size": config.FC_SIZE,
    }

    with open(model_manifest_path, 'w') as fp:
        json.dump(modelManifest, fp)

    # save vocabulary file
    model_vocab_path = os.path.join(config.JOB_ID, "vocab.pth")
    torch.save(vocab, model_vocab_path)

    # archive model files for torch serving
    handler = os.path.join(pathlib.Path(__file__).parent.resolve(), 'torchserve_handler.py')
    extra_files = model_manifest_path + "," + model_vocab_path
    archive_model_name = config.MODEL_NAME + "_" + config.MODEL_VERSION
    archive_model_file = os.path.join(config.JOB_ID, archive_model_name + ".mar")
    if os.path.exists(archive_model_file):
        os.remove(archive_model_file)

    model_archiver.archive(model_name=archive_model_name, handler_file=handler, model_state_file=model_local_path,
        extra_files=extra_files, model_version=config.MODEL_SERVING_VERSION, dest_path=config.JOB_ID)

    # upload model files to minio storage, mini-automl-ms/run_{jobId}/...
    # store four files per model: model.pth, vocab.pth, manifest.json and model.mar.
    client.fput_object(config.MODEL_BUCKET, os.path.join(config.MODEL_OBJECT_NAME, "model.pth"), model_local_path)
    client.fput_object(config.MODEL_BUCKET, os.path.join(config.MODEL_OBJECT_NAME, "vocab.pth"), model_vocab_path)
    client.fput_object(config.MODEL_BUCKET, os.path.join(config.MODEL_OBJECT_NAME, "manifest.json"), model_manifest_path)
    client.fput_object(config.MODEL_BUCKET, os.path.join(config.MODEL_OBJECT_NAME, "model.mar"), archive_model_file)

    artifact = orca3_utils.create_artifact(config.MODEL_BUCKET, config.MODEL_OBJECT_NAME, algorithm_name)

    print("saved model file as artifact {} version {} at {}/{}".format(artifact.artifact.name, artifact.version,
                                                                       config.MODEL_BUCKET, config.MODEL_OBJECT_NAME))
    orca3_utils.log_run_end(True, 'test accuracy {:8.3f}'.format(accu_test))
