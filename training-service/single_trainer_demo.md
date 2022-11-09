# Single Trainer Training Demo

> Please run all scripts from the repository's root directory.

Please go over the [Data Management Function Demo](../data-management/demo.md) first.
This demo depends on the dataset created in that demo.
Take note of the value of `datasetId` in that demo.
In the following instructions, we will assume `datasetId` is `1`.
We will also need both the MinIO server and the Data Management server running from that demo.

## Step 1: Start the Metadata Store Server and the Training Server

Run the following to start the Metadata Store server:
```shell
./scripts/ms-002-start-server.sh
```

The script will start the Metadata Store server container using the image `orca3/services:latest`.

If this image does not exist on your machine, Docker will attempt pulling it from the Docker Hub.

The script will name the container `metadata-store`, launch it in network `orca3`, and bind it to port `6002`.

The server will be accessible at `localhost:6002` from your machine, or `metadata-store:51001` from other containers within the same network `orca3`.


You should see the following when you run the script:
```shell
Started metadata-store docker container and listen on port 6002
```

Next, run the following:
```shell
./scripts/ts-001-start-server.sh
```

The script will start the Training Service server container using the image `orca3/services:latest`.

The script will name the container `training-service`, launch it in network `orca3`, and bind it to port `6003`.

The server will be accessible at `localhost:6002` from your machine, or `training-service:51001` from other containers within the same network `orca3`.

You should see the following when you run the script:
```shell
Started training-service docker container and listen on port 6003
```

## Step 2: Submit a training job

> The following instructions assume a value of `1` for `datasetId`. 
> Make sure the value is the one that you received from the [Data Management Function Demo](../data-management/demo.md).

> **If you are running on Apple Silicon**, you may need to perform
> ```shell
> docker pull orca3/intent-classification
> ```
> before submitting a training job.

Run the following (replace `1` with your `datasetId` if needed):
```shell
./scripts/ts-002-start-run.sh 1
```

The script will:
1. Invoke the `Train` API method of the Training Server to start an `intent-classification` training job on the dataset with `datasetId=1`,
   using training parameter `LR=4;EPOCHS=15;BATCH_SIE=64;FC_SIZE=128;`.
2. The API method will respond with `jobId`, which we can use to track the training status.

You should see the following when you run the script:
```shell
{
  "job_id": 1
}
```

## Step 3: Inspect the training job's status

Run the following (replace `1` if you have a different `job_id` from the step above):
```shell
./scripts/ts-003-check-run.sh 1
```

This script will invoke the `GetTrainingStatus` API method of the Training Server to query the training job's status.

As the job progresses, you may see it in different statuses. Here are some examples.

### Job is in the queue
```shell
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
```

### Job is being launched
```shell
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
```

### Job is running
```shell
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
```

### Job completed successfully
```shell
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

### Job failed due to missing image
If you run into an error like
```shell
{
  "status": "failure",
  "job_id": 1,
  "message": "Status 404: {\"message\":\"No such image: orca3/intent-classification:latest\"}\n",
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
run
```shell
docker pull orca3/intent-classification:latest
```
and retry from Step 2.

## Clean up

> If you would like to run the [distributed training service lab (Chapter 4)](distributed_trainer_demo.md), skip this step and keep containers running.
> They will provide the required dataset.

Run the following:
```shell
./scripts/lab-999-tear-down.sh
```
