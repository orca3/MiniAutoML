#!/usr/bin/env bash
if [ "$(docker ps -a | grep minio)" ]; then
  docker stop minio
fi
if [ "$(docker ps -a | grep data-management)" ]; then
  docker stop data-management
fi
if [ "$(docker ps -a | grep prediction-service)" ]; then
  docker stop prediction-service
fi
if [ "$(docker ps -a | grep metadata-store)" ]; then
  docker stop metadata-store
fi
if [ "$(docker ps -a | grep training-service)" ]; then
  docker stop training-service
fi
if [ "$(docker ps -a | grep intent-classification-predictor)" ]; then
  docker stop intent-classification-predictor
fi
if [ "$(docker ps -a | grep intent-classification-predictor)" ]; then
  docker stop intent-classification-torch-predictor
fi
if [ "$(docker ps -a | grep local-docker-registry)" ]; then
  docker stop local-docker-registry
fi
