#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ "$1" != "" ] && [ "$2" != "" ]; then
    echo "Fetching dataset $1 with version $2"
    grpcurl -plaintext \
      -d "{\"dataset_id\": \"$1\", \"version_hash\": \"$2\"}" \
      localhost:"${DM_PORT}" data_management.DataManagementService/FetchTrainingDataset
else
    echo "Requires dataset_id as the first parameter"
    echo "Requires version_hash as the second parameter"
fi

