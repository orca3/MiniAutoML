#!/usr/bin/env bash
source "$(dirname "$0")/ms-000-env-vars.sh"

echo
run_id="${1:-1}"
echo "Starting run $run_id"
grpcurl -plaintext \
  -d "{
  \"start_time\": \"2021-08-01T00:00:00Z\",
  \"run_id\": \"$run_id\",
  \"run_name\": \"demo-run\",
  \"tracing\": {
    \"dataset_id\": \"1\",
    \"version_hash\": \"hashBA==\",
    \"code_version\": \"12a3bfd\"
  }
}" \
  localhost:"${MS_PORT}" metadata_store.MetadataStoreService/LogRunStart
