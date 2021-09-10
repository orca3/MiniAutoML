#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ ! "$(docker ps -a | grep data-management)" ]; then
  # Todo: replace when we published our container
  docker build -t orca3/services:latest -f services.dockerfile .
  docker run --name data-management --network orca3 --rm -d -p "${DM_PORT}":51001 orca3/services:latest data-management.jar
  echo "Started data-management docker container and listen on port ${DM_PORT}"
else
  echo "data-management docker container is already running"
fi
