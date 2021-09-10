#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
run_id="${1:-1}"
epoch_id="${2:-1}"
echo "Posting epoch $epoch_id for run $run_id"
grpcurl -plaintext \
  -d "{
  \"epoch_info\": {
    \"start_time\":\"2021-08-01T01:00:00Z\",
    \"end_time\":\"2021-08-01T01:30:00Z\",
    \"run_id\":\"$run_id\",
    \"epoch_id\":\"$epoch_id\",
    \"metrics\": {\"foo_metrics\":\"bar_value\"}
  }
}" \
  localhost:"${MS_PORT}" metadata_store.MetadataStoreService/LogEpoch
