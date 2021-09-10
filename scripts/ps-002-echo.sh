#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
grpcurl -plaintext \
  -d '{"message": "hello world"}' \
  localhost:"${PS_PORT}" prediction.PredictionService/Echo
