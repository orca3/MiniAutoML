#!/usr/bin/env bash
source "$(dirname "$0")/ms-000-env-vars.sh"

echo
run_id="${1:-1}"
echo "Finishing run $run_id"
grpcurl -plaintext \
  -d "{
  \"end_time\":\"2021-08-01T02:00:00Z\",
  \"run_id\":\"$run_id\",
  \"success\":true,
  \"message\":\"$run_id successfully completed\"
}" \
  localhost:"${MS_PORT}" metadata_store.MetadataStoreService/LogRunEnd
