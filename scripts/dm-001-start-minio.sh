#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ ! "$(docker ps -a | grep minio)" ]; then
  echo "Start minio docker container and listen on port 9000"
  docker run --name minio --rm -d -p 9000:9000 -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD minio/minio server /data
else
  echo "Minio docker container is already running"
fi
