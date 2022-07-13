#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps | grep training-service)" ]; then
  echo "training-service docker container is already running, stop it"
  docker stop training-service
fi

if [ ! "$(docker ps -a | grep training-service)" ]; then
  docker run --name training-service \
    --network orca3 \
    --rm -d \
    -p "${TS_PORT}":51001 \
    -v $HOME/.kube/config:/.kube/config \
    --env APP_CONFIG=config/config-docker-kube.properties \
    "${IMAGE_NAME}" \
    training-service.jar
  echo "Started training-service docker container and listen on port ${TS_PORT}"
else
  echo "training-service docker container is already running"
fi
