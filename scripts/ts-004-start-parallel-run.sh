#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
grpcurl -plaintext \
  -d "{
  \"metadata\": {
    \"algorithm\":\"intent-classification\",
    \"dataset_id\":\"1\",
    \"name\":\"test-parallel\",
    \"train_data_version_hash\":\"hashDg==\",
    \"output_model_name\":\"my-parallel-intent-classification-model\",
    \"parameters\": {
      \"LR\":\"4\",
      \"EPOCHS\":\"10\",
      \"BATCH_SIZE\":\"64\",
      \"PARALLEL_INSTANCES\":\"3\",
      \"FC_SIZE\":\"128\"
    }
  }
}" \
  localhost:${TS_PORT} training.TrainingService/Train
