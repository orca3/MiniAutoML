#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo "Building orca3/services"
docker build \
-t orca3/services:latest \
-f "$(dirname "$0")/../services.dockerfile" \
"$(dirname "$0")/.."
echo ""

echo "Building orca3/intent-classification-predictor"
docker build \
-t orca3/intent-classification-predictor:latest \
-f "$(dirname "$0")/../predictor/Dockerfile" \
"$(dirname "$0")/../predictor"
echo ""

echo "Building orca3/intent-classification & orca3/intent-classification-torch"
docker build \
-t orca3/intent-classification \
-t orca3/intent-classification-torch \
-f "$(dirname "$0")/../training-code/text-classification/Dockerfile" \
"$(dirname "$0")/../training-code/text-classification"
echo ""

