#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"
echo "Start minio docker container and listen on port 9000"
docker run --rm -d -p 9000:9000 -e MINIO_ROOT_USER -e MINIO_ROOT_PASSWORD minio/minio server /data
