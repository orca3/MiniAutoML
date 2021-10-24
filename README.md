# MiniAutoML

This repository contains source code examples for `<<bookname>>`.

## System Requirement
The installation of system requirements are not included in the `scripts` folder. Please make sure those requirements are met before executing scripts in the `scripts` folder.

- **Operating system**: MacOs or Linux or WSL (Windows Subsystem for Linux).
- **Java JDK 11+**: 
  - Use `java --version` command to confirm your Java version.
  - Apache maven is not required to run the examples. We've bundled [Maven wrapper](https://github.com/takari/maven-wrapper) `mvnw` so that all the build commands we used in this repo depends only on `mvnw`.
- **Docker**: docker community edition can be downloaded from https://docs.docker.com/get-docker/. 
  - Use `docker version` command to verify both the client and the server are available/running.
- **Kubernetes**: docker community edition provides a standalone node kubernetes installation. You can enable it by following [official doc](https://docs.docker.com/desktop/kubernetes).
  - Use `kubectl version` command to verify both the client and the server are available/running.
- **Minio**: this is a storage system that we used in our examples to provide a shared file system for all our microservices. **Only the client is needed** (we will take care of starting/stopping server later in examples).
[The official doc](https://docs.min.io/docs/minio-client-quickstart-guide.html) talks about several ways to install it. We need the binary version (so that `mc` command is available).
  - For mac, do `brew install minio/stable/mc`.
  - For linux, follow the official doc to download the right minio client binary for your platform.
  - Use `mc --version` command to verify it has been successfully installed.
- **Grpcurl**: we found this [grpcurl](https://github.com/fullstorydev/grpcurl) tool a great way to demo grpc services in the commandline environment, so our example scripts use it extensively.
  - For mac, do `brew install grpcurl`
  - For linux, follow the project documentation to download the right binary
  - Use `grpcurl --version` command to verify it has been successfully installed.

## Module list

In the root folder you'll find a Maven project description file `pom.xml`, which describes a multi-module Java project. 
- `grpc-contract` module contains shared microservices grpc definitions as well as code generation automations.
- `data-management`, `metadata-store`, `prediction-service`, `training-service` each contains a runnable service comprising the Deep Learning System introduced in the book. The readme in the corresponding module talks about how to use it.
- `training-code` contains deep learning model training code for text classification, written in Python. [training-code/text-classification/Readme.md](training-code/text-classification/) talks about how to setup the Python environment.
- `scripts` contains demo bash scripts used in the `<<lab chapter>>` as well as individual module's readme file. We expect those scripts to be executed using repository root as the working directory.
- Dockerfile (`services.dockerfile`) builds all these microservices, producing ONE image that is capable of starting multiple services. Providing `<<module-name>>.jar` to the argument section of the `docker run` command can start the corresponding microservice. You can see example `docker run` command in [scripts/dm-002-start-server.sh](scripts/dm-002-start-server.sh).

## Data Management Example

6. Get the file link for version `hashDg==`: `./scripts/dm-007-fetch-dataset-version.sh 1 hashDg==`
```
Fetching dataset 1 with version hashDg==
{
  "dataset_id": "1",
  "version_hash": "hashDg==",
  "state": "READY",
  "parts": [
    {
      "name": "examples.csv",
      "bucket": "mini-automl-dm",
      "path": "versionedDatasets/1/hashDg==/examples.csv"
    },
    {
      "name": "labels.csv",
      "bucket": "mini-automl-dm",
      "path": "versionedDatasets/1/hashDg==/labels.csv"
    }
  ],
  "statistics": {
    "numExamples": "16200",
    "numLabels": "151"
  }
}
```
7. 
8. Prepare a version of dataset 1 using tag filters: `./scripts/dm-006-prepare-partial-dataset.sh 1 "training set"`
```
Prepare a version of dataset 1 that contains only training data with tag category:training set
{
  "dataset_id": "1",
  "name": "dataset-1",
  "dataset_type": "TEXT_INTENT",
  "last_updated_at": "2021-07-14T06:23:20.814986Z",
  "version_hash": "hashBA==",
  "commits": [
    {
      "dataset_id": "1",
      "commit_id": "2",
      "created_at": "2021-07-14T06:24:52.973920Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "training set"
        }
      ],
      "path": "dataset/1/commit/2",
      "statistics": {
        "numExamples": "7600",
        "numLabels": "151"
      }
    }
  ]
}
```
8. Get the file link for version `hashBA==`: `./scripts/dm-007-fetch-dataset-version.sh 1 hashBA==`
```
Fetching dataset 1 with version hashBA==
{
  "dataset_id": "1",
  "version_hash": "hashBA==",
  "state": "READY",
  "parts": [
    {
      "name": "examples.csv",
      "bucket": "mini-automl-dm",
      "path": "versionedDatasets/1/hashBA==/examples.csv"
    },
    {
      "name": "labels.csv",
      "bucket": "mini-automl-dm",
      "path": "versionedDatasets/1/hashBA==/labels.csv"
    }
  ],
  "statistics": {
    "numExamples": "7600",
    "numLabels": "151"
  }
}
```
9. To inspect file content, setup mc alias first: 
```
source ./scripts/dm-000-env-vars.sh
mc alias -q set myminio http://127.0.0.1:"${MINIO_PORT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
```
10. Then copy file from minio to local for your inspection
```
mc cp myminio/mini-automl-dm/versionedDatasets/1/hashDg==/training.csv hashDg==/training.csv
mc cp myminio/mini-automl-dm/versionedDatasets/1/hashDg==/examples.csv hashDg==/examples.csv
head hashDg==/training.csv
head hashDg==/examples.csv
```
