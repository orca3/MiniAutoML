#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo "gitsha=\"$(git rev-parse --short HEAD)\"" > "$(dirname "$0")/../training-code/text-classification/version.py"

docker build \
-t localhost:${REGISTRY_PORT}/orca3/intent-classification \
-t localhost:${REGISTRY_PORT}/orca3/intent-classification-torch \
-t orca3/intent-classification \
-t orca3/intent-classification-torch \
-f "$(dirname "$0")/../training-code/text-classification/Dockerfile" \
"$(dirname "$0")/../training-code/text-classification"
