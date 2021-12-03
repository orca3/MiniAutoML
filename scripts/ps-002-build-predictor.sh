#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

mkdir -p model_cache
MODEL_CACHE_DIR="$(pwd)/model_cache"

if [ ! "$(docker ps -a | grep intent-classification-predictor)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/intent-classification-predictor:latest -f predictor/Dockerfile predictor
  docker run --name intent-classification-predictor --network orca3 --rm -d -p "${ICP_PORT}":51001 -v "${MODEL_CACHE_DIR}":/models orca3/intent-classification-predictor:latest
  echo "Started intent-classification-predictor docker container and listen on port ${ICP_PORT}"
else
  echo "intent-classification-predictor docker container is already running"
fi

grpcurl -plaintext \
  -d "{
    \"algorithm\": \"intent-classification\",
    \"backend\": \"orca3\",
    \"host\": \"localhost\",
    \"port\": ${ICP_PORT}
  }" \
  localhost:"${PS_PORT}" prediction.PredictionService/RegisterPredictor
