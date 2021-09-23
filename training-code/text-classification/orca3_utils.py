import os
import sys
from datetime import datetime

import grpc

import metadata_store_pb2
import metadata_store_pb2_grpc


class Orca3Utils:
    def __init__(self, host: str, port: int, run_id: str, rank: int, dataset_id: str, version_hash: str,
                 code_version: str):
        channel = grpc.insecure_channel('%s:%d'.format(host, port))
        self.stub = metadata_store_pb2_grpc.MetadataStoreServiceStub(channel)
        self.run_id = run_id
        self.rank = rank
        self.tracing = metadata_store_pb2.TracingInformation(
            dataset_id=dataset_id,
            version_hash=version_hash,
            code_version=code_version
        )

    def log_run_start(self):
        self.stub.LogRunStart(metadata_store_pb2.LogRunStartRequest(
            start_time=datetime.now().isoformat(),
            run_id=self.run_id,
            run_name="master" if self.rank == 0 else "worker-%d".format(self.rank),
            tracing=self.tracing
        ))


class TrainingConfig:
    @staticmethod
    def int_or_default(variable, default):
        if variable is None:
            return default
        else:
            return int(variable)

    def __str__(self) -> str:
        results = [
            "{}={}".format("EPOCHS", self.EPOCHS), "{}={}".format("LR", self.LR),
            "{}={}".format("BATCH_SIZE", self.BATCH_SIZE),
            "{}={}".format("FC_SIZE", self.FC_SIZE),
            "{}={}".format("MINIO_SERVER", self.MINIO_SERVER),
            "{}={}".format("MINIO_SERVER_ACCESS_KEY", self.MINIO_SERVER_ACCESS_KEY),
            "{}={}".format("MINIO_SERVER_SECRET_KEY", self.MINIO_SERVER_SECRET_KEY),
            "{}={}".format("TRAINING_DATA_BUCKET", self.TRAINING_DATA_BUCKET),
            "{}={}".format("TRAINING_DATA_PATH", self.TRAINING_DATA_PATH),
            "{}={}".format("MODEL_BUCKET", self.MODEL_BUCKET),
            "{}={}".format("MODEL_ID", self.MODEL_ID),
            "{}={}".format("MODEL_VERSION", self.MODEL_VERSION),
            "{}={}".format("WORLD_SIZE", self.WORLD_SIZE),
            "{}={}".format("RANK", self.RANK),
            "{}={}".format("MASTER_ADDR", self.MASTER_ADDR),
            "{}={}".format("MASTER_PORT", self.MASTER_PORT),
        ]
        return "\n".join(results)

    def __init__(self):
        self.EPOCHS = self.int_or_default(os.getenv('EPOCHS'), 20)
        self.LR = self.int_or_default(os.getenv('LR'), 5)
        self.BATCH_SIZE = self.int_or_default(os.getenv('BATCH_SIZE'), 64)
        self.FC_SIZE = self.int_or_default(os.getenv('FC_SIZE'), 128)
        self.MINIO_SERVER = os.getenv('MINIO_SERVER') or "127.0.0.1:9000"
        self.MINIO_SERVER_ACCESS_KEY = os.getenv('MINIO_SERVER_ACCESS_KEY') or "foooo"
        self.MINIO_SERVER_SECRET_KEY = os.getenv('MINIO_SERVER_SECRET_KEY') or "barbarbar"
        self.TRAINING_DATA_BUCKET = os.getenv('TRAINING_DATA_BUCKET') or "mini-automl-dm"
        self.TRAINING_DATA_PATH = os.getenv('TRAINING_DATA_PATH') or "versionedDatasets/1/hashDg=="
        self.MODEL_BUCKET = os.getenv('MODEL_BUCKET') or "mini-automl-serving"
        self.MODEL_ID = os.getenv('MODEL_ID') or "aaf98dsfase"
        self.MODEL_VERSION = os.getenv('MODEL_VERSION') or "1"
        self.model_path = "{0}/{1}".format(self.MODEL_ID, self.MODEL_VERSION)
        # distributed training related settings
        self.WORLD_SIZE = self.int_or_default(os.getenv('WORLD_SIZE'), 1)
        self.RANK = self.int_or_default(os.getenv('RANK'), 0)
        self.MASTER_ADDR = os.getenv('MASTER_ADDR') or "localhost"
        os.environ['MASTER_ADDR'] = self.MASTER_ADDR
        self.MASTER_PORT = os.getenv('MASTER_PORT') or "12356"
        os.environ['MASTER_PORT'] = self.MASTER_PORT
        if len(sys.argv) == 3:
            self.RANK = int(sys.argv[1])
            self.WORLD_SIZE = int(sys.argv[2])
