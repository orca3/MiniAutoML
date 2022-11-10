# Distributed Trainer Training Demo

> Please run all scripts from the repository's root directory.

> If you are coming from the single trainer training demo, and have skipped the cleanup step at the end, proceed to Step 1.

Please go over the [Data Management Function Demo](../data-management/demo.md) first.
This demo depends on the dataset created in that demo.
Take note of the value of `datasetId` in that demo.
In the following instructions, we will assume `datasetId` is `1`.
We will also need both the MinIO server and the Data Management server running from that demo.

## Step 1: Start the Metadata Store Server and the Training Server

> If you are coming from the single trainer training demo, and have skipped the cleanup step at the end, you can skip starting the Metadata Store server.

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

**IMPORTANT**: Before starting the Training Server, make sure Kubernetes is up and running.
Run
```shell
kubectl version --output=yaml
```
and you should get something similar to
```shell
clientVersion:
  buildDate: "2022-09-21T14:33:49Z"
  compiler: gc
  gitCommit: 5835544ca568b757a8ecae5c153f317e5736700e
  gitTreeState: clean
  gitVersion: v1.25.2
  goVersion: go1.19.1
  major: "1"
  minor: "25"
  platform: darwin/arm64
kustomizeVersion: v4.5.7
serverVersion:
  buildDate: "2022-09-21T14:27:13Z"
  compiler: gc
  gitCommit: 5835544ca568b757a8ecae5c153f317e5736700e
  gitTreeState: clean
  gitVersion: v1.25.2
  goVersion: go1.19.1
  major: "1"
  minor: "25"
  platform: linux/arm64
```

Also make sure the `orca3` namespace is present:
```shell
NAME                   STATUS   AGE
default                Active   22h
kube-node-lease        Active   22h
kube-public            Active   22h
kube-system            Active   22h
kubernetes-dashboard   Active   21h
orca3                  Active   3m55s
```
If the namespace is not present, create it with:
```shell
kubectl create namespace orca3
```
If Kubernetes is not running, the Training Server container will quit during startup, after it fails to locate Kubernetes.

If the Kubernetes namespace is not present, Training Server will not be able to launch distributed training pods on Kubernetes.

Next, run the following:
```shell
./scripts/ts-001-start-server-kube.sh
```

The script will start the Training Service server container using the image `orca3/services:latest`, with a configuration
that uses Kubernetes as its backend. The script will stop any pre-existing Training Service server.

The script will name the container `training-service`, launch it in network `orca3`, and bind it to port `6003`.

The server will be accessible at `localhost:6003` from your machine, or `training-service:51001` from other containers within the same network `orca3`.

You should see the following when you run the script:
```shell
Started training-service docker container and listen on port 6003
```

## Step 2: Submit training job

> The following instructions assume a value of `1` for `datasetId`.
> Make sure the value is the one that you received from the [Data Management Function Demo](../data-management/demo.md).

Run the following (replace `1` with your `datasetId` if needed):
```shell
./scripts/ts-004-start-parallel-run.sh 1
```

The script will:
1. Invoke the `Train` API method of the Training Server to start an `intent-classification` training job on the dataset with `datasetId=1`,
   with an extra parameter `PARALLEL_INSTANCES=3;`
2. The API method will respond with `jobId`, which we can use to track the training status.

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

## Step 4: Check execution backend status

In this demo we are using Kubernetes as the backend.
To see our training pods, run:
```shell
kubectl get pods --namespace orca3
```

This will display all pods created and managed by the Training Service.

In the example output below,
1. the non-distributed training job with `job_id=1` launched only one pod with a `job-1` name prefix;
2. the distributed training job with `job_id=2` with a parallelism of 3, launched three pods with `job-2` name prefixes.

```shell
NAME                                         READY   STATUS    RESTARTS   AGE
job-1-1635150586093-test1-master             1/1     Running   0          11s
job-2-1635150590973-test-parallel-master     1/1     Running   0          6s
job-2-1635150590973-test-parallel-worker-1   1/1     Running   0          6s
job-2-1635150590973-test-parallel-worker-2   1/1     Running   0          6s
```

## Clean up
Run the following:
```shell
./scripts/lab-999-tear-down.sh
```
