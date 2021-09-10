#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep metadata-store)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/services:latest -f services.dockerfile .
  docker run --name metadata-store --network orca3 --rm -d -p "${MS_PORT}":51001 orca3/services:latest metadata-store.jar
  echo "Started metadata-store docker container and listen on port ${MS_PORT}"
else
  echo "metadata-store docker container is already running"
fi
