## Function Demo
> All the following scripts are expected to be executed from the repository root directory

> Please make sure all the [system requirements](https://github.com/orca3/MiniAutoML#system-requirements) are installed before running this demo.

The following guide will go through a typical cycle of `create dataset` -> `add more commits to the dataset` -> `retrieve the entire dataset or a subset of it` -> `fetch the repackaged dataset for training`


### Setup
Execute: `./scripts/dm-001-start-minio.sh`

This will:
1. Create a docker network `orca3` if needed
2. Start a minio server with name `minio` in docker joining network `orca3` and bind to port 9000. Minio server is now accessible at `localhost:9000` from your machine, or `minio:9000` from other containers connected to docker network `orca3`
3. Minio server uses credentials stored in `./scripts/env-vars.sh`

Expected output:
```
Created docker network orca3
Started minio docker container and listen on port 9000
```

Execute: `./scripts/dm-002-start-server.sh`

This will:
1. Build a docker image using `services.dockerfile` in the root directory
2. Start a data-management server with name `data-management` in docker joining network `orca3` and bind to port 6000. Data management server is now accessible at `localhost:6000` from your machine, or `data-management:51001` from other containers connected to docker network `orca3`.

Expected output:
```
Started data-management docker container and listen on port 6000
```

### Create intent dataset
Execute: `./scripts/dm-003-create-dataset.sh`

This will:
1. Upload our sample data `data-management/src/test/resources/datasets/demo-part1.csv` into minio server
2. Invoke `CreateDataset` endpoint using predefined payload
3. Got a datasetId in the reply. In this case, `1`

Expected output:
> note the date / datasetId can be slightly different from the actual result you are seeing
```
Upload raw data to cloud object storage to get a data url. For demo purpose, we upload data to 'mini-automl-dm' bucket in the local MinIO server, data url to reference the data is 'upload/001.csv'
`data-management/src/test/resources/datasets/test.csv` -> `myminio/mini-automl-dm/upload/001.csv`
Total: 0 B, Transferred: 281.90 KiB, Speed: 10.63 MiB/s

Creating intent dataset
{
  "dataset_id": "1",
  "name": "dataset-1",
  "dataset_type": "TEXT_INTENT",
  "last_updated_at": "2021-07-14T06:23:20.814986Z",
  "commits": [
    {
      "dataset_id": "1",
      "commit_id": "1",
      "created_at": "2021-07-14T06:23:21.517873Z",
      "commit_message": "Initial commit",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/1",
      "statistics": {
        "numExamples": "5500",
        "numLabels": "151"
      }
    }
  ]
}
```

### Add more commits to dataset
Execute: `./scripts/dm-004-add-commits.sh 1`.
> Please update `1` to the datasetId you received in the previous command.

This will:
1. Upload our sample data `data-management/src/test/resources/datasets/demo-part2.csv` and `data-management/src/test/resources/datasets/demo-part3.csv` into minio server
2. Invoke `UpdateDataset` endpoint to update the dataset with provided datasetId `1`

Expected output:
> note the date / datasetId can be slightly different from the actual result you are seeing
```
Uploading new data file
`data-management/src/test/resources/datasets/demo-part2.csv` -> `myminio/mini-automl-dm/upload/002.csv`
Total: 0 B, Transferred: 398.27 KiB, Speed: 20.82 MiB/s
`data-management/src/test/resources/datasets/demo-part3.csv` -> `myminio/mini-automl-dm/upload/003.csv`
Total: 0 B, Transferred: 161.40 KiB, Speed: 12.72 MiB/s

Adding new commit to dataset 1
...
{
  "dataset_id": "1",
  "name": "dataset-1",
  "dataset_type": "TEXT_INTENT",
  "last_updated_at": "2021-07-14T06:23:20.814986Z",
  "commits": [
    {
      "dataset_id": "1",
      "commit_id": "1",
      "created_at": "2021-07-14T06:23:21.517873Z",
      "commit_message": "Initial commit",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/1",
      "statistics": {
        "numExamples": "5500",
        "numLabels": "151"
      }
    },
    {
      "dataset_id": "1",
      "commit_id": "2",
      "created_at": "2021-07-14T06:24:52.973920Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/2",
      "statistics": {
        "numExamples": "7600",
        "numLabels": "151"
      }
    },
    {
      "dataset_id": "1",
      "commit_id": "3",
      "created_at": "2021-07-14T06:24:53.215104Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "bbb"
        }
      ],
      "path": "dataset/1/commit/3",
      "statistics": {
        "numExamples": "3100",
        "numLabels": "151"
      }
    }
  ]
}
```

### Request the content of the entire dataset
Execute: `./scripts/dm-005-prepare-dataset.sh 1`
> Please update `1` to the datasetId you used in the previous command.

This will:
1. Invoke `PrepareTrainingDataset` endpoint to submit a data repackaging task
2. This task will merge all 3 commits in dataset 1 into one package when finished
3. You get a `version_hash` in the reply, with which you can track the repackaging status in later steps. In this example, the version hash is`hashDg==`

Expected output:
```
Prepare a version of dataset 1 that contains all commits
{
  "dataset_id": "1",
  "name": "dataset-1",
  "dataset_type": "TEXT_INTENT",
  "last_updated_at": "2021-07-14T06:23:20.814986Z",
  "version_hash": "hashDg==",
  "commits": [
    {
      "dataset_id": "1",
      "commit_id": "1",
      "created_at": "2021-07-14T06:23:21.517873Z",
      "commit_message": "Initial commit",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/1",
      "statistics": {
        "numExamples": "5500",
        "numLabels": "151"
      }
    },
    {
      "dataset_id": "1",
      "commit_id": "2",
      "created_at": "2021-07-14T06:24:52.973920Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/2",
      "statistics": {
        "numExamples": "7600",
        "numLabels": "151"
      }
    },
    {
      "dataset_id": "1",
      "commit_id": "3",
      "created_at": "2021-07-14T06:24:53.215104Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "bbb"
        }
      ],
      "path": "dataset/1/commit/3",
      "statistics": {
        "numExamples": "3100",
        "numLabels": "151"
      }
    }
  ]
}

``` 

### Request a filtered version of the dataset
Execute: `./scripts/dm-006-prepare-partial-dataset.sh 1 "aaa"`
> Please update `1` to the datasetId you used in the previous command.

This will:
1. Invoke `PrepareTrainingDataset` endpoint to submit a data repacking task, which also includes a filter `category=aaa`
2. This task will merge the 2 matching commits in dataset 1 into one package when finished
3. You get a `version_hash` in the reply, with which you can track the repackaging status in later steps. In this example, the version_hash is `hashBg==`

Expected output:
```
Prepare a version of dataset 1 that contains only training data with tag category:aaa
{
  "dataset_id": "1",
  "name": "dataset-1",
  "dataset_type": "TEXT_INTENT",
  "last_updated_at": "2021-07-14T06:23:20.814986Z",
  "version_hash": "hashBg==",
  "commits": [
    {
      "dataset_id": "1",
      "commit_id": "1",
      "created_at": "2021-07-14T06:23:21.517873Z",
      "commit_message": "Initial commit",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
        }
      ],
      "path": "dataset/1/commit/1",
      "statistics": {
        "numExamples": "5500",
        "numLabels": "151"
      }
    },
    {
      "dataset_id": "1",
      "commit_id": "2",
      "created_at": "2021-07-14T06:24:52.973920Z",
      "commit_message": "More training data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "aaa"
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

### Check the data preparation task status
Execute `./scripts/dm-007-fetch-dataset-version.sh 1 hashDg==` to check the task status.
> Note you can try the other version hash `hashBg==` as well

This will:
1. Invoke `FetchTrainingDataset` endpoint to check the status
2. You'll get the state of the repackaged data as well as the location on minio where the repackaged data lives

Expected output:
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
  "root": {
    "name": "root",
    "bucket": "mini-automl-dm",
    "path": ""versionedDatasets/1/hashDg=="
  },
  "statistics": {
    "numExamples": "16200",
    "numLabels": "151"
  }
}
```

### Inspect the repackaged file content
Execute `mc ls myminio/mini-automl-dm/versionedDatasets/1/hashDg==/` to look at the files, or use `mc cp myminio/mini-automl-dm/versionedDatasets/1/hashDg==/examples.csv examples.csv` to download the file to current working directly

This will:
1. Copy the output of the data preparation task from the `mini-automl-dm` bucket in `myminio` connection (set up already in previous commends)

If for some reason `myminio` alias has not been set up yet, you can manually set it up using
```
source ./scripts/env-vars.sh
mc alias -q set myminio http://127.0.0.1:"${MINIO_PORT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
```

## Clean up
>If you are going to run the training service lab (chapter 5 and 6), please don't execute the tear-down script and keep the containers running, they will provide the dataset required for the [training lab](https://github.com/orca3/MiniAutoML/blob/main/training-service/demo.md).

Execute `./scripts/lab-999-tear-down.sh`

