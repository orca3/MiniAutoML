#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
if [ "$1" != "" ]; then
    echo "Check the status of run $1"
    grpcurl -plaintext \
      -d "{
        \"runId\": \"$1\",
        \"document\": \"merry chirstmas\"
      }" \
      localhost:"${PS_PORT}" prediction.PredictionService/Predict
else
    echo "Requires run_id as the first parameter"
fi

