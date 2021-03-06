## Distributed Trainer Training Demo
> All the following scripts are expected to be executed from the repository root directory
 
Please go over the data-management [demo](../data-management/demo.md) first. We need the datasetId from the previous demo here. Assuming it is dataset 1.

### Setup
Execute: `./scripts/ms-002-start-server.sh`

This will
1. Build a docker image using `services.dockerfile` in the root directory.
2. Start a metadata-store server with name `metadata-store` in docker joining network `orca3`

Expected output:
```
Started metadata-store docker container and listen on port 6002
```


Execute: `./scripts/ts-001-start-server-kube.sh`

This will
1. Build a docker image using `services.dockerfile` in the root directory.
2. Start a training-service server with name `training-service` in docker joining network `orca3`

```
Started training-service docker container and listen on port 6003
```

### Submit training job
> Assuming the datasetId from [data-management demo](../data-management/demo.md) is 1. 

Execute: ` ./scripts/ts-004-start-parallel-run.sh 1`, replace 1 with other dataset id if needed.

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
Execute `./scripts/lab-999-tear-down.sh`
