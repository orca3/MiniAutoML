#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
grpcurl -plaintext \
  -d "{
    \"runId\": \"1\",
    \"document\": \"merry chirstmas\"
  }" \
  localhost:"${PS_PORT}" prediction.PredictionService/Predict
