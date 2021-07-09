#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"
mc alias set myminio http://127.0.0.1:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

echo
echo "Upload dataset"
mc -q cp data-management/src/test/resources/datasets/test.csv myminio/mini-automl/upload/001.csv
echo
echo "Creating intent dataset"
grpcurl -v -plaintext \
  -d '{"name": "dataset-1", "dataset_type": "TEXT_INTENT", "bucket": "mini-automl", "path": "upload/001.csv"}' \
  localhost:51001 data_management.DataManagementService/CreateDataset
