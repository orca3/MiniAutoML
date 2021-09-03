# MiniAutoML

System Requirement:
- JDK 11
- Docker
- [Minio client](https://docs.min.io/docs/minio-client-quickstart-guide.html) `brew install minio/stable/mc`

## Data Management Example

1. Start minio server using docker: `./scripts/dm-001-start-minio.sh`
```
Started minio docker container and listen on port 9000
```
2. Run data management server: `./scripts/dm-002-start-server.sh`
```
Started data-management docker container and listen on port 5000
```
3. In another tab, create a dataset: `./scripts/dm-003-create-dataset.sh`
```
Upload dataset
`data-management/src/test/resources/datasets/test.csv` -> `myminio/mini-automl-dm/upload/001.csv`
Total: 0 B, Transferred: 281.90 KiB, Speed: 14.64 MiB/s

Creating intent dataset
{
  "datasetId": "1",
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
          "tag_value": "test set"
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
4. Add more commits to dataset: `./scripts/dm-004-add-commits.sh 1`
```
Uploading new data file
`data-management/src/test/resources/datasets/train.csv` -> `myminio/mini-automl-dm/upload/002.csv`
Total: 0 B, Transferred: 398.27 KiB, Speed: 20.82 MiB/s
`data-management/src/test/resources/datasets/validation.csv` -> `myminio/mini-automl-dm/upload/003.csv`
Total: 0 B, Transferred: 161.40 KiB, Speed: 12.72 MiB/s

Adding new commit to dataset 1
...
{
  "datasetId": "1",
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
          "tag_value": "test set"
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
          "tag_value": "training set"
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
      "commit_message": "More validation data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "validation set"
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
5. Prepare a version of dataset 1 with all commits: `./scripts/dm-005-prepare-dataset.sh 1`
```
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
          "tag_value": "test set"
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
          "tag_value": "training set"
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
      "commit_message": "More validation data",
      "tags": [
        {
          "tag_key": "category",
          "tag_value": "validation set"
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
7. Prepare a version of dataset 1 using tag filters: `./scripts/dm-006-prepare-partial-dataset.sh 1 "training set"`
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
