#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
if [ "$1" != "" ]; then
    echo "Check the status of run $1"
    grpcurl -plaintext \
      -d "{\"job_id\": \"$1\"}" \
      localhost:"${TS_PORT}" training.TrainingService/GetTrainingStatus
else
    echo "Requires run_id as the first parameter"
fi
