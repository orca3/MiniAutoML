#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep prediction-service)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/services:latest -f services.dockerfile .
  docker run --name prediction-service --network orca3 --rm -d -p "${PS_PORT}":51001 orca3/services:latest prediction-service.jar
  echo "Started prediction-service docker container and listen on port ${PS_PORT}"
else
  echo "prediction-service docker container is already running"
fi
