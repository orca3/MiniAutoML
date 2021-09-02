#!/usr/bin/env bash
source "$(dirname "$0")/ts-000-env-vars.sh"

if [ ! "$(docker ps -a | grep training-service)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/training-service:latest -f training-service.dockerfile .
  docker run --name training-service --network orca3 --rm -d -p "${TS_PORT}":51001 orca3/training-service:latest
  echo "Started training-service docker container and listen on port ${TS_PORT}"
else
  echo "training-service docker container is already running"
fi
