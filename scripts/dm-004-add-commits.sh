#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"
mc alias -q set myminio http://127.0.0.1:"${MINIO_PORT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

if [ "$1" != "" ]; then
    echo
    echo "Uploading new data file"
    mc -q cp data-management/src/test/resources/datasets/train.csv myminio/"${MINIO_DM_BUCKET}"/upload/002.csv
    mc -q cp data-management/src/test/resources/datasets/validation.csv myminio/"${MINIO_DM_BUCKET}"/upload/003.csv
    echo
    echo "Adding new commit to dataset $1"
    grpcurl -plaintext \
      -d "{\"dataset_id\": \"$1\", \"commit_message\": \"More training data\", \"bucket\": \"${MINIO_DM_BUCKET}\", \"path\": \"upload/002.csv\", \"tags\": [{\"tag_key\": \"category\", \"tag_value\": \"training set\"}]}" \
      localhost:"${DM_PORT}" data_management.DataManagementService/UpdateDataset
    grpcurl -plaintext \
      -d "{\"dataset_id\": \"$1\", \"commit_message\": \"More validation data\", \"bucket\": \"${MINIO_DM_BUCKET}\", \"path\": \"upload/003.csv\", \"tags\": [{\"tag_key\": \"category\", \"tag_value\": \"validation set\"}]}" \
      localhost:"${DM_PORT}" data_management.DataManagementService/UpdateDataset
else
    echo "Requires dataset_id as the first parameter"
fi

