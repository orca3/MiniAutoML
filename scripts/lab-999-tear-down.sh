#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

function tear_down() {
    container_name=$1
  if docker ps -a | grep -q "$container_name" ; then
    docker stop "$container_name"
    docker rm "$container_name"
  fi
}

tear_down "minio"
tear_down "data-management"
tear_down "prediction-service"
tear_down "metadata-store"
tear_down "training-service"
tear_down "intent-classification-predictor"
tear_down "intent-classification-torch-predictor"
