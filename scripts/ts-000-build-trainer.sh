#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ ! "$(docker ps -a | grep local-docker-registry)" ]; then
  # Todo: replace when we published our container
  docker run --rm -d --name local-docker-registry -p ${REGISTRY_PORT}:5000 registry
  echo "Started local-docker-registry docker container and listen on port ${REGISTRY_PORT}"
else
  echo "local-docker-registry docker container is already running"
fi

echo "gitsha=\"$(git rev-parse --short HEAD)\"" > "$(dirname "$0")/../training-code/text-classification/version.py"

docker build \
-t localhost:${REGISTRY_PORT}/orca3/intent-classification \
-t orca3/intent-classification \
-f "$(dirname "$0")/../training-code/text-classification/Dockerfile" \
"$(dirname "$0")/../training-code/text-classification"
docker push localhost:${REGISTRY_PORT}/orca3/intent-classification
