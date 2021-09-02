#!/usr/bin/env bash
source "$(dirname "$0")/ts-000-env-vars.sh"

echo
grpcurl -plaintext \
  -d "{
  \"metadata\":\"2021-08-01T02:00:00Z\",
  \"run_id\":\"$run_id\",
  \"success\":true,
  \"message\":\"$run_id successfully completed\"
}" \
  localhost:"${TS_PORT}" training.TrainingService/Train
