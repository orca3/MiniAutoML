#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ "$1" != "" ]; then
    echo "Prepare a version of dataset $1 that contains all commits"
    grpcurl -plaintext \
      -d "{\"dataset_id\": \"$1\"}" \
      localhost:51001 data_management.DataManagementService/PrepareTrainingDataset
else
    echo "Requires dataset_id as the first parameter"
fi

