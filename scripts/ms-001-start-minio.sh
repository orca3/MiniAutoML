#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker network ls | grep orca3)" ]; then
  docker network create orca3
  echo "Created docker network orca3"
else
  echo "Docker network orca3 already exists"
fi

if [ ! "$(docker ps -a | grep minio)" ]; then
  docker run --name minio --network orca3 --rm -d -p "${MINIO_PORT}":9000 -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD minio/minio server /data
  echo "Started minio docker container and listen on port 9000"
else
  echo "Minio docker container is already running"
fi
