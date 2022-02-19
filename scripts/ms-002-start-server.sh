#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep metadata-store)" ]; then
  docker run --name metadata-store \
    --network orca3 \
    --rm -d \
    -p "${MS_PORT}":51001 \
    "${IMAGE_NAME}" \
    metadata-store.jar
  echo "Started metadata-store docker container and listen on port ${MS_PORT}"
else
  echo "metadata-store docker container is already running"
fi
