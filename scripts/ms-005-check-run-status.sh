#!/usr/bin/env bash
source "$(dirname "$0")/ms-000-env-vars.sh"

echo
run_id="${1:-1}"
echo "Checking run $run_id's status"
grpcurl -plaintext \
  -d "{
  \"run_id\":\"$run_id\"
}" \
  localhost:"${MS_PORT}" metadata_store.MetadataStoreService/GetRunStatus
