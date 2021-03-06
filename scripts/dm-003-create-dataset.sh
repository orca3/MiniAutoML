#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"
mc alias -q set myminio http://127.0.0.1:"${MINIO_PORT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo
echo "Upload raw data to cloud object storage to get a data url. For demo purpose, we upload data to 'mini-automl-dm' bucket in the local MinIO server, data url to reference the data is 'upload/001.csv'"
mc -q cp data-management/src/test/resources/datasets/demo-part1.csv myminio/"${MINIO_DM_BUCKET}"/upload/001.csv
echo
echo "Creating intent dataset"
grpcurl -plaintext \
  -d '{"name": "dataset-1", "dataset_type": "TEXT_INTENT", "bucket": "mini-automl-dm", "path": "upload/001.csv", "tags": [{"tag_key": "category", "tag_value": "aaa"}]}' \
  localhost:"${DM_PORT}" data_management.DataManagementService/CreateDataset
