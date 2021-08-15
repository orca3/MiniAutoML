#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"
mc alias -q set myminio http://127.0.0.1:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo
echo "Upload raw data to cloud object storage to get a data url. For demo purpose, we upload data to 'mini-automl-dm' bucket in the local MinIO server, data url to reference the data is 'upload/001.csv'"
mc -q cp data-management/src/test/resources/datasets/test.csv myminio/mini-automl-dm/upload/001.csv
echo
echo "Creating intent dataset"
grpcurl -plaintext \
  -d '{"name": "dataset-1", "dataset_type": "TEXT_INTENT", "bucket": "mini-automl-dm", "path": "upload/001.csv", "tags": [{"tag_key": "category", "tag_value": "test set"}]}' \
  localhost:"${DM_PORT}" data_management.DataManagementService/CreateDataset
