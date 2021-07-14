#!/usr/bin/env bash
source "$(dirname "$0")/dm-000-env-vars.sh"

if [ "$1" != "" ] && [ "$2" != "" ]; then
    echo "Prepare a version of dataset $1 that contains only training data with tag category:$2"
    grpcurl -plaintext \
      -d "{\"dataset_id\": \"$1\", \"tags\":[{\"tag_key\":\"category\", \"tag_value\":\"$2\"}]}" \
      localhost:51001 data_management.DataManagementService/PrepareTrainingDataset
else
    echo "Requires dataset_id as the first parameter"
    echo "Requires tag_value as the second parameter"

fi

