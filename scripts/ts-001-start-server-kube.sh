#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep training-service)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/services:latest -f services.dockerfile .
  docker run --name training-service -v $HOME/.kube/config:/.kube/config --env APP_CONFIG=config-kube.properties --network orca3 --rm -d -p "${TS_PORT}":51001 orca3/services:latest training-service.jar
  echo "Started training-service docker container and listen on port ${TS_PORT}"
else
  echo "training-service docker container is already running"
fi
