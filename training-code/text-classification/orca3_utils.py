import os
import sys
from datetime import datetime
from typing import Dict

import grpc

import data_management_pb2
import metadata_store_pb2
import metadata_store_pb2_grpc
from version import gitsha

from model_archiver.manifest_components.manifest import RuntimeType
from model_archiver.model_packaging import generate_model_archive, package_model
from model_archiver.model_packaging_utils import ModelExportUtils


class Orca3Utils:
    def __init__(self, metadata_store_url: str, job_id: str, rank: int, dataset_id: str, version_hash: str,
                 code_version: str, model_name: str):
        channel = grpc.insecure_channel(metadata_store_url)
        self.stub = metadata_store_pb2_grpc.MetadataStoreServiceStub(channel)
        self.run_id = job_id
        self.rank = rank
        self.tracing = metadata_store_pb2.TracingInformation(
            dataset_id=dataset_id,
            version_hash=version_hash,
            code_version=code_version
        )
        self.model_name = model_name

    def log_run_start(self):
        return self.stub.LogRunStart(metadata_store_pb2.LogRunStartRequest(
            start_time=datetime.now().isoformat(),
            run_id=self.run_id,
            run_name="training job {}".format(self.run_id),
            tracing=self.tracing
        ))

    def log_run_end(self, is_success: bool, message: str):
        return self.stub.LogRunEnd(metadata_store_pb2.LogRunEndRequest(
            run_id=self.run_id,
            end_time=datetime.now().isoformat(),
            success=is_success,
            message=message,
        ))

    def create_artifact(self, model_bucket: str, model_object_name: str):
        return self.stub.CreateArtifact(metadata_store_pb2.CreateArtifactRequest(
            artifact=data_management_pb2.FileInfo(
                name=self.model_name,
                bucket=model_bucket,
                path=model_object_name,
            ),
            run_id=self.run_id,
        ))

    def log_epoch(self, started: str, epoch_id: int, metrics: Dict[str, str]):
        return self.stub.LogEpoch(metadata_store_pb2.LogEpochRequest(
            epoch_info=metadata_store_pb2.EpochInfo(
                start_time=started,
                end_time=datetime.now().isoformat(),
                run_id=self.run_id,
                epoch_id="{}-{}".format(self.rank, epoch_id),
                metrics=metrics
            )))


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
            "{}={}".format("METADATA_STORE_SERVER", self.METADATA_STORE_SERVER),
            "{}={}".format("MINIO_SERVER", self.MINIO_SERVER),
            "{}={}".format("MINIO_SERVER_ACCESS_KEY", self.MINIO_SERVER_ACCESS_KEY),
            "{}={}".format("MINIO_SERVER_SECRET_KEY", self.MINIO_SERVER_SECRET_KEY),
            "{}={}".format("TRAINING_DATASET_ID", self.TRAINING_DATASET_ID),
            "{}={}".format("TRAINING_DATASET_VERSION_HASH", self.TRAINING_DATASET_VERSION_HASH),
            "{}={}".format("TRAINING_DATA_BUCKET", self.TRAINING_DATA_BUCKET),
            "{}={}".format("TRAINING_DATA_PATH", self.TRAINING_DATA_PATH),
            "{}={}".format("JOB_ID", self.JOB_ID),
            "{}={}".format("MODEL_NAME", self.MODEL_NAME),
            "{}={}".format("MODEL_BUCKET", self.MODEL_BUCKET),
            "{}={}".format("MODEL_VERSION", self.MODEL_VERSION),
            "{}={}".format("MODEL_SERVING_VERSION", self.MODEL_SERVING_VERSION),
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
        self.METADATA_STORE_SERVER = os.getenv('METADATA_STORE_SERVER') or "127.0.0.1:5002"
        self.JOB_ID = os.getenv('JOB_ID') or "42"
        self.MINIO_SERVER = os.getenv('MINIO_SERVER') or "127.0.0.1:9000"
        self.MINIO_SERVER_ACCESS_KEY = os.getenv('MINIO_SERVER_ACCESS_KEY') or "foooo"
        self.MINIO_SERVER_SECRET_KEY = os.getenv('MINIO_SERVER_SECRET_KEY') or "barbarbar"
        self.TRAINING_DATASET_ID = os.getenv('TRAINING_DATASET_ID') or "1"
        self.TRAINING_DATASET_VERSION_HASH = os.getenv('TRAINING_DATASET_VERSION_HASH') or "hashDg=="
        self.TRAINING_DATA_BUCKET = os.getenv('TRAINING_DATA_BUCKET') or "mini-automl-dm"
        self.TRAINING_DATA_PATH = os.getenv('TRAINING_DATA_PATH') or "versionedDatasets/1/hashDg=="
        self.MODEL_NAME = os.getenv('MODEL_NAME') or "intent"
        self.MODEL_BUCKET = os.getenv('MODEL_BUCKET') or "mini-automl-ms"
        self.MODEL_VERSION = gitsha
        self.MODEL_SERVING_VERSION = os.getenv('MODEL_SERVING_VERSION') or "1.0"
        self.MODEL_OBJECT_NAME = "model"
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

class TorchModelArchiver:
    class TorchArchiveArgs:
        def __init__(self, **kwargs):
            self.__dict__.update(kwargs)

        def update(self, **kwargs):
            self.__dict__.update(kwargs)

    def archive(self, model_name, handler_file, model_state_file, extra_files, model_version, dest_path) -> None:
        args = self.TorchArchiveArgs(model_name=model_name, handler=handler_file, runtime=RuntimeType.PYTHON.value, model_file=None,
                serialized_file=model_state_file, extra_files=extra_files, export_path=dest_path, force=False,
                archive_format="default", convert=False, version=model_version, source_vocab=None,
                requirements_file=None)

        return package_model(args, ModelExportUtils.generate_manifest_json(args))
 


