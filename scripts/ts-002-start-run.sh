#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
grpcurl -plaintext \
  -d "{
  \"metadata\": {
    \"algorithm\":\"intent-classification\",
    \"dataset_id\":\"1\",
    \"name\":\"test1\",
    \"train_data_version_hash\":\"hashBA==\",
    \"parameters\": {
      \"LR\":\"4\",
      \"EPOCHS\":\"15\",
      \"BATCH_SIZE\":\"64\",
      \"FC_SIZE\":\"128\"
    }
  }
}" \
  localhost:5003 training.TrainingService/Train

# kick off distributed training (3 parallel instances)
echo
grpcurl -plaintext \
  -d "{
  \"metadata\": {
    \"algorithm\":\"intent-classification\",
    \"dataset_id\":\"1\",
    \"name\":\"test1\",
    \"train_data_version_hash\":\"hashDg==\",
    \"parameters\": {
      \"LR\":\"4\",
      \"EPOCHS\":\"15\",
      \"BATCH_SIZE\":\"64\",
      \"PARALLEL_INSTANCES\":\"3\",
      \"FC_SIZE\":\"128\"
    }
  }
}" \
  localhost:5003 training.TrainingService/Train
