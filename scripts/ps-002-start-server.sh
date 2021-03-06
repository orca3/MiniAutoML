#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

mkdir -p model_cache
MODEL_CACHE_DIR="$(pwd)/model_cache"

if [ ! "$(docker ps -a | grep prediction-service)" ]; then
  docker run --name prediction-service \
    --network orca3 \
    --rm -d \
    -p "${PS_PORT}":51001 \
    -v "${MODEL_CACHE_DIR}":/tmp/modelCache \
    "${IMAGE_NAME}" \
    prediction-service.jar
  echo "Started prediction-service docker container and listen on port ${PS_PORT}"
else
  echo "prediction-service docker container is already running"
fi
