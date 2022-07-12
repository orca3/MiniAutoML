#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if ! docker network ls | grep -q orca3 ; then
  docker network create orca3
  echo "Created docker network orca3"
else
  echo "Docker network orca3 already exists"
fi

if ! docker ps -a | grep -q minio ; then
  docker run --name minio \
    --network orca3 \
    -d \
    -p "${MINIO_PORT}":9000 \
    -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD \
    minio/minio server /data
  echo "Started minio docker container and listen on port 9000"
else
  echo "Minio docker container is already running"
fi

if ! docker ps -a | grep -q data-management ; then
  docker run --name data-management \
    --network orca3 \
    -d \
    -p "${DM_PORT}":51001 \
    "${IMAGE_NAME}" \
    data-management.jar
  echo "Started data-management docker container and listen on port ${DM_PORT}"
else
  echo "data-management docker container is already running"
fi

if ! docker ps -a | grep -q metadata-store ; then
  docker run --name metadata-store \
    --network orca3 \
    -d \
    -p "${MS_PORT}":51001 \
    "${IMAGE_NAME}" \
    metadata-store.jar
  echo "Started metadata-store docker container and listen on port ${MS_PORT}"
else
  echo "metadata-store docker container is already running"
fi

rm -r model_cache
mkdir -p model_cache
MODEL_CACHE_DIR="$(pwd)/model_cache"

if ! docker ps -a | grep -q intent-classification-predictor ; then
  docker run --name intent-classification-predictor \
    --network orca3 \
    -d \
    -p "${ICP_PORT}":51001 \
    -v "${MODEL_CACHE_DIR}":/models \
    orca3/intent-classification-predictor:latest
  echo "Started intent-classification-predictor docker container and listen on port ${ICP_PORT}"
else
  echo "intent-classification-predictor docker container is already running"
fi

if ! docker ps -a | grep -q intent-classification-torch-predictor ; then
  docker run --name intent-classification-torch-predictor \
    --network orca3 \
    -d \
    -p "${ICP_TORCH_PORT}":7070 -p "${ICP_TORCH_MGMT_PORT}":7071 \
    -v "${MODEL_CACHE_DIR}":/models \
    -v "$(pwd)/config/torch_server_config.properties":/home/model-server/config.properties \
    pytorch/torchserve:0.5.2-cpu torchserve \
    --start --model-store /models
  echo "Started intent-classification-torch-predictor docker container and listen on port ${ICP_TORCH_PORT} & ${ICP_TORCH_MGMT_PORT}"
else
  echo "intent-classification-torch-predictor docker container is already running"
fi

if ! docker ps -a | grep -q prediction-service ; then
  docker run --name prediction-service \
    --network orca3 \
    -d \
    -p "${PS_PORT}":51001 \
    -v "${MODEL_CACHE_DIR}":/tmp/modelCache \
    "${IMAGE_NAME}" \
    prediction-service.jar
  echo "Started prediction-service docker container and listen on port ${PS_PORT}"
else
  echo "prediction-service docker container is already running"
fi

if ! docker image ls | grep -vq predictor | grep -q "orca3/intent-classification" ; then
  docker pull orca3/intent-classification:latest
  echo "pull intent-classification training image"
else
  echo "intent-classification image already exists"
fi

if ! docker ps -a | grep -q training-service ; then
  docker run --name training-service \
    --network orca3 \
    -d \
    -p "${TS_PORT}":51001 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    "${IMAGE_NAME}" \
    training-service.jar
  echo "Started training-service docker container and listen on port ${TS_PORT}"
else
  echo "training-service docker container is already running"
fi
