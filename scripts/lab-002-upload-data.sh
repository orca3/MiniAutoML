#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

mc alias -q set myminio http://127.0.0.1:"${MINIO_PORT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

conda install -c huggingface -c conda-forge datasets~=1.18.0
python3 "$(dirname "$0")/prepare_data.py"


echo
echo "Upload raw data to cloud object storage to get a data url'"
mc -q cp tweet_emotion_part1.csv myminio/"${MINIO_DM_BUCKET}"/upload/tweet_emotion_part1.csv
mc -q cp tweet_emotion_part2.csv myminio/"${MINIO_DM_BUCKET}"/upload/tweet_emotion_part2.csv
echo
echo "Creating intent dataset"

grpcurl -plaintext \
  -d '{"name": "tweet_emotion", "dataset_type": "TEXT_INTENT", "bucket": "mini-automl-dm", "path": "upload/tweet_emotion_part1.csv"}' \
  localhost:"${DM_PORT}" data_management.DataManagementService/CreateDataset
