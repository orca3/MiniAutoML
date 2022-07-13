#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo "dataset_id is $1"
dataset_id=$1

function prepare_dataset() {
  grpcurl -plaintext \
    -d "{\"dataset_id\": \"$1\"}" \
    localhost:"${DM_PORT}" data_management.DataManagementService/PrepareTrainingDataset
}

_temp=$(prepare_dataset "$dataset_id")
version_hash=$(echo -n "$_temp" | jq ".version_hash")

echo "version_hash is $version_hash"

echo
grpcurl -plaintext \
  -d "{
  \"metadata\": {
    \"algorithm\":\"intent-classification\",
    \"dataset_id\":\"$1\",
    \"name\":\"test1\",
    \"train_data_version_hash\":$version_hash,
    \"output_model_name\":\"my-intent-classification-model\",
    \"parameters\": {
      \"LR\":\"4\",
      \"EPOCHS\":\"15\",
      \"BATCH_SIZE\":\"64\",
      \"FC_SIZE\":\"128\"
    }
  }
}" \
  localhost:"${TS_PORT}" training.TrainingService/Train
