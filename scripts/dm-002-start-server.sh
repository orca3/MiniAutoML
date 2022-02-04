#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep data-management)" ]; then
  docker run --name data-management \
    --network orca3 \
    --rm -d \
    -p "${DM_PORT}":51001 \
    "${IMAGE_NAME}" \
    data-management.jar
  echo "Started data-management docker container and listen on port ${DM_PORT}"
else
  echo "data-management docker container is already running"
fi
