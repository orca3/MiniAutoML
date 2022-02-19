#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

mkdir -p model_cache
MODEL_CACHE_DIR="$(pwd)/model_cache"

if [ ! "$(docker ps -a | grep intent-classification-predictor)" ]; then
  docker run --name intent-classification-predictor \
    --network orca3 \
    --rm -d \
    -p "${ICP_PORT}":51001 \
    -v "${MODEL_CACHE_DIR}":/models \
    orca3/intent-classification-predictor:latest
  echo "Started intent-classification-predictor docker container and listen on port ${ICP_PORT}"
else
  echo "intent-classification-predictor docker container is already running"
fi

if [ ! "$(docker ps -a | grep intent-classification-torch-predictor)" ]; then
  docker run --name intent-classification-torch-predictor \
    --network orca3 \
    --rm -d \
    -p "${ICP_TORCH_PORT}":7070 -p "${ICP_TORCH_MGMT_PORT}":7071 \
    -v "${MODEL_CACHE_DIR}":/models \
    -v "$(pwd)/config/torch_server_config.properties":/home/model-server/config.properties \
    pytorch/torchserve:0.5.2-cpu torchserve \
    --start --model-store /models
  echo "Started intent-classification-torch-predictor docker container and listen on port ${ICP_TORCH_PORT} & ${ICP_TORCH_MGMT_PORT}"
else
  echo "intent-classification-torch-predictor docker container is already running"
fi

