#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ ! "$(docker ps -a | grep data-management)" ]; then
  docker run --name data-management --network orca3 --rm -d -p "${DM_PORT}":51001 orca3/data-management:latest
  echo "Started data-management docker container and listen on port 5000"
else
  echo "data-management docker container is already running"
fi
