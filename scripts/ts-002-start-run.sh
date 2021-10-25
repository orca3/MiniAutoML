#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
grpcurl -plaintext \
  -d "{
  \"metadata\": {
    \"algorithm\":\"intent-classification\",
    \"dataset_id\":\"1\",
    \"name\":\"test1\",
    \"train_data_version_hash\":\"hashDg==\",
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
