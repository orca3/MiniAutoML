# Data Management Function Demo

> Please run all scripts from the repository's root directory.

> Please make sure all [system requirements](https://github.com/orca3/MiniAutoML#system-requirements) are met before running this demo.

The following guide will go through a typical cycle of
1. Create dataset
2. Add more commits to a dataset
3. Retrieve the entire dataset and a subset of it
4. Fetch the repackaged dataset for training

## Step 1: Starting MinIO

Run the following:
```shell
./scripts/dm-001-start-minio.sh
```

The script will:
1. Create a Docker network `orca3` if needed.
2. Start a MinIO server container named `minio` in network `orca3`, and bind it to port `9000`.
   MinIO server will be accessible at `localhost:9000` from your machine, or `minio:9000` from other containers within the same Docker network `orca3`.
3. MinIO server uses credentials stored in `./scripts/env-vars.sh`

You should see the following when you run the script:
```shell
Created docker network orca3
Started minio docker container and listen on port 9000
```

## Step 2: Starting Data Management Server

Run the following:
```shell
./scripts/dm-002-start-server.sh
```

The script will:
1. Launch the Docker image `orca3/services:latest`. If this image does not exist on your machine, Docker will attempt pulling it from the Docker Hub.
2. Start a Data Management server container named `data-management` in network `orca3`, and bind to port `6000`.
   Data management server will be accessible at `localhost:6000` from your machine, or `data-management:51001` from other containers within the same Docker network `orca3`.

You should see the following when you run the script:
```shell
Started data-management docker container and listen on port 6000
```

## Step 3: Creating an intent dataset

Run the following:
```shell
./scripts/dm-003-create-dataset.sh
```

The script will:
1. Upload sample data from `data-management/src/test/resources/datasets/demo-part1.csv` to the MinIO server.
2. Invoke the `CreateDataset` API method of the Data Management Server to create a dataset with the sample data that was uploaded to the MinIO server. 
3. Extract the `datasetId` from the API method's response. In our example, the value is `1`.

You should see the following when you run the script:
```shell
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

**IMPORTANT:** You may get a different `datasetId` if you have created other datasets before.
In that case, you will need to use that value for subsequent steps where `datasetId` is required.

## Step 4: Add a new commit to the dataset

Run the following:
```shell
./scripts/dm-004-add-commits.sh 1
```

> Update `1` to the `datasetId` that was returned from the previous Step 3.

The script will:
1. Upload the following sample data to the MinIO server:
   * `data-management/src/test/resources/datasets/demo-part2.csv`
   * `data-management/src/test/resources/datasets/demo-part3.csv`
2. Invoke the `UpdateDataset` API method of the Data Management Server to update the dataset created in Step 3.

You should see the following when you run the script:
```shell
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

## Step 5: Retrieve the content of the entire dataset

Run the following:
```shell
./scripts/dm-005-prepare-dataset.sh 1
```
> Update `1` to the `datasetId` that was returned from Step 3.

The script will:
1. Invoke the `PrepareTrainingDataset` API method of the Data Management Server to submit a data preparation task.
2. This task will merge all 3 commits in the dataset to a versioned snapshot.
3. A `version_hash` will be returned as part of the API method response.
   It can be used to track the data preparation status in subsequent steps.
   In this example, the returned value of version hash is `hashDg==`.

You should see the following when you run the script:
```shell
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

## Step 6: Retrieve a filtered dataset

Run the following:
```shell
./scripts/dm-006-prepare-partial-dataset.sh 1 "aaa"
```
> Update `1` to the `datasetId` that was returned from Step 3.

The script will:
1. Invoke the `PrepareTrainingDataset` API method of the Data Management Server to submit a data preparation task, with a filter parameter `category=aaa`.
2. This task will merge the 2 matching commits in the dataset to a versioned snapshot.
3. A `version_hash` will be returned as part of the API method response.
   It can be used to track the data preparation status in subsequent steps.
   In this example, the returned value of `version_hash` is `hashBg==`.

You should see the following when you run the script:
```shell
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

### Step 7: Check the status of the data preparation task

Run the following:
```shell
./scripts/dm-007-fetch-dataset-version.sh 1 hashDg==
```
> You can try the other version hash `hashBg==` as well.

The script will:
1. Invoke the `FetchTrainingDataset` API method of the Data Management Server to check the status of the data preparation task specified by `version_hash`.
2. You will get the status of the data preparation task, as well as the location of the dataset snapshot on MinIO.

You should see the following when you run the script:
```shell
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

### Step 8: Inspect the content of the dataset snapshot

To list all files of the dataset snapshot, run:
```shell
mc ls myminio/mini-automl-dm/versionedDatasets/1/hashDg==/
```

To download a file from the dataset snapshot to the current working directory, run:
```shell
mc cp myminio/mini-automl-dm/versionedDatasets/1/hashDg==/examples.csv examples.csv
```

## Step 9: Clean up

> If you are going to run the training service lab (Chapter 3 and 4), skip the tear down step.
> The dataset created in this lab is required by both the
> [single trainer training demo](https://github.com/orca3/MiniAutoML/blob/main/training-service/single_trainer_demo.md) and the
> [distributed trainer training demo](https://github.com/orca3/MiniAutoML/blob/main/training-service/distributed_trainer_demo.md).

Run the following:
```shell
./scripts/lab-999-tear-down.sh
```
