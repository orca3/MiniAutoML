#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ "$1" == "" ]; then
    echo "Requires model_id/run_id as the first parameter"
    exit 1
fi

# model_id is run_id and job_id from training service
model_id=$1
document=$2

echo "model_id is $model_id"
echo "document is $document"

grpcurl -plaintext \
  -d "{
    \"runId\": \"$model_id\",
    \"document\": \"$document\"
  }" \
  localhost:"${PS_PORT}" prediction.PredictionService/Predict

