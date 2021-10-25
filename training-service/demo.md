## Function Demo
> All the following scripts are expected to be executed from the repository root directory
 
Please go over the data-management [demo](../data-management/demo.md) first. We need the datasetId from the previous demo here. Assuming it is dataset 1.

### Setup
Execute: `./scripts/ts-000-build-trainer.sh`

This will
1. Launch a local docker registry container on port 3000. 
Without this our kubernetes backend cannot pull text classification trainer image.
2. Build the trainer image `orca3/intent-classification:latest` from python code in [training-code](../training-code)
3. "Push" the trainer image to our local registry as `localshot:3000/orca3/intent-classification`
4. `localhost:3000/orca3/intent-classification` is the actual image name we used in kubernetes to launch our trainer pod

Expected output:
```
8ba739593e0bda889c50fd8f2a080d92f175070efa521934e260bee84df6b8b1
Started local-docker-registry docker container and listen on port 3000
[+] Building 3.4s (14/14) FINISHED
 => [internal] load build definition from Dockerfile                                                                      0.0s
 => => transferring dockerfile: 574B                                                                                      0.0s
 => [internal] load .dockerignore                                                                                         0.0s
 => => transferring context: 2B                                                                                           0.0s
 => [internal] load metadata for docker.io/pytorch/pytorch:1.9.0-cuda10.2-cudnn7-runtime                                  2.0s
 => [auth] pytorch/pytorch:pull token for registry-1.docker.io                                                            0.0s
 => [1/8] FROM docker.io/pytorch/pytorch:1.9.0-cuda10.2-cudnn7-runtime@sha256:5dc11a9036bcb5b7950f4f8a43974057559278fa4b  0.0s
 => [internal] load build context                                                                                         0.0s
 => => transferring context: 161.72kB                                                                                     0.0s
 => CACHED [2/8] RUN pip3 install minio protobuf grpcio                                                                   0.0s
 => CACHED [3/8] RUN mkdir /opt/intent-classification                                                                     0.0s
 => [4/8] COPY *.py /opt/intent-classification/                                                                           0.0s
 => [5/8] WORKDIR /opt/intent-classification                                                                              0.0s
 => [6/8] RUN mkdir /model                                                                                                0.3s
 => [7/8] RUN mkdir /logs                                                                                                 0.3s
 => [8/8] RUN chgrp -R 0 /opt/intent-classification   && chmod -R g+rwX /opt/intent-classification   && chgrp -R 0 /mode  0.3s
 => exporting to image                                                                                                    0.1s
 => => exporting layers                                                                                                   0.1s
 => => writing image sha256:f22797d695bb83474c589113d444ed1fec978339f31a98432366c051adf69c93                              0.0s
 => => naming to localhost:3000/orca3/intent-classification                                                               0.0s
 => => naming to docker.io/orca3/intent-classification                                                                    0.0s

Use 'docker scan' to run Snyk tests against images to find vulnerabilities and learn how to fix them
Using default tag: latest
The push refers to repository [localhost:3000/orca3/intent-classification]
5f106a47c789: Pushed
5924beb5f5af: Pushed
2d8328bc51d7: Pushed
5f70bf18a086: Pushed
7cd21bf4cc43: Pushed
d44ff5fa395a: Pushed
875029d95deb: Pushed
0e8f8d4f0dae: Pushed
36ddb277a164: Pushed
4bd1c3d7150d: Pushed
5f08512fd434: Pushed
c7bb31fc0e08: Pushed
50858308da3d: Pushed
latest: digest: sha256:3e4c57aef08c4f67d91fab584bb83bfb0c7db8dd33e3f3b7b662134fb0c08da1 size: 3032
```

Execute: `./scripts/ms-002-start-server.sh`

This will
1. Build a docker image using `services.dockerfile` in the root directory.
2. Start a metadata-store server with name `metadata-store` in docker joining network `orca3`

Expected output:
```
Started metadata-store docker container and listen on port 5002
```

Execute: `./scripts/ts-001-start-server-kube.sh`

This will
1. Build a docker image using `services.dockerfile` in the root directory.
2. Start a training-service server with name `training-service` in docker joining network `orca3`

```
Started training-service docker container and listen on port 5003
```

### Submit training job
> Assuming the datasetId from [data-management demo](../data-management/demo.md) is 1. 
> If this is not the case please update [ts-002-start-run.sh](../scripts/ts-002-start-run.sh)

Execute: `./scripts/ts-002-start-run.sh`

This will
1. Invoke `Train` endpoint to start an `intent-classification` training on dataset `1` version `hashDg==`,
with parameter `LR=4;EPOCHS=15;BATCH_SIE=64;FC_SIZE=128;`
2. Reply is the jobId that we can use to track the status later

Expected output:
```
{
  "job_id": 1
}
```

Execute: ` ./scripts/ts-004-start-parallel-run.sh`

This will
1. Invoke `Train` endpoint to start an `intent-classification` training on dataset `1` version `hashDg==`,
   with an extra parameter `PARALLEL_INSTANCES=3;`
2. Reply is the jobId that we can use to track the status later

### Train job status
Execute: `./scripts/ts-003-check-run.sh 1`. Replace `1` with another value if your job_id is different.

This will
1. Invoke `GetTrainingStatus` endpoint to query the execution status

Expected output, based on the timing, it can be one of the following:
```
{
  "job_id": 1,
  "message": "Queueing, there are 0 training jobs waiting before this.",
  "metadata": {
    "algorithm": "intent-classification",
    "dataset_id": "1",
    "name": "test1",
    "train_data_version_hash": "hashDg==",
    "parameters": {
      "BATCH_SIZE": "64",
      "EPOCHS": "15",
      "FC_SIZE": "128",
      "LR": "4"
    },
    "output_model_name": "my-intent-classification-model"
  }
}

{
  "status": "launch",
  "job_id": 1,
  "metadata": {
    "algorithm": "intent-classification",
    "dataset_id": "1",
    "name": "test1",
    "train_data_version_hash": "hashDg==",
    "parameters": {
      "BATCH_SIZE": "64",
      "EPOCHS": "15",
      "FC_SIZE": "128",
      "LR": "4"
    },
    "output_model_name": "my-intent-classification-model"
  }
}

{
  "status": "running",
  "job_id": 1,
  "metadata": {
    "algorithm": "intent-classification",
    "dataset_id": "1",
    "name": "test1",
    "train_data_version_hash": "hashDg==",
    "parameters": {
      "BATCH_SIZE": "64",
      "EPOCHS": "15",
      "FC_SIZE": "128",
      "LR": "4"
    },
    "output_model_name": "my-intent-classification-model"
  }
}

{
  "status": "succeed",
  "job_id": 1,
  "metadata": {
    "algorithm": "intent-classification",
    "dataset_id": "1",
    "name": "test1",
    "train_data_version_hash": "hashDg==",
    "parameters": {
      "BATCH_SIZE": "64",
      "EPOCHS": "15",
      "FC_SIZE": "128",
      "LR": "4"
    },
    "output_model_name": "my-intent-classification-model"
  }
}
```

### Check execution backend status
In this demo we are using kubernetes backend. Execute `kubectl get pods --namespace orca3` to see all the pods

This will display all the pods created and managed by the training-service.
1. For non-distributed training job `1`, there will be only one pod bearing `job-1` prefix.
2. For distributed training job `2` with a parallelism of 3, there will be three pods bearing `job-2` prefix.

Expected output:
```
NAME                                         READY   STATUS    RESTARTS   AGE
job-1-1635150586093-test1-master             1/1     Running   0          11s
job-2-1635150590973-test-parallel-master     1/1     Running   0          6s
job-2-1635150590973-test-parallel-worker-1   1/1     Running   0          6s
job-2-1635150590973-test-parallel-worker-2   1/1     Running   0          6s
```

## Clean up
Execute `./scripts/tear-down.sh`
