#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

if [ "$1" == "" ]; then
    echo "Requires dataset_id as the first parameter"
    exit 1
fi

dataset_id=$1
echo "dataset_id is $dataset_id"

function update_dataset() {
  grpcurl -plaintext \
    -d "{
    \"dataset_id\": \"$1\",
    \"commit_message\": \"tweet_emotion_part2\",
    \"bucket\": \"${MINIO_DM_BUCKET}\",
    \"path\": \"upload/tweet_emotion_part2.csv\"
    }" \
    localhost:"${DM_PORT}" data_management.DataManagementService/UpdateDataset
}

update_dataset "$dataset_id"

function prepare_dataset() {
  grpcurl -plaintext \
    -d "{\"dataset_id\": \"$1\"}" \
    localhost:"${DM_PORT}" data_management.DataManagementService/PrepareTrainingDataset
}

_temp=$(prepare_dataset "$dataset_id")
version_hash=$(echo -n "$_temp" | jq ".version_hash")

echo "version_hash is $version_hash"

function start_training() {
  grpcurl -plaintext \
    -d "{
    \"metadata\": {
      \"algorithm\":\"intent-classification\",
      \"dataset_id\":\"$1\",
      \"name\":\"test1\",
      \"train_data_version_hash\":$2,
      \"output_model_name\":\"second-iteration-model\",
      \"parameters\": {
        \"LR\":\"50\",
        \"EPOCHS\":\"15\",
        \"BATCH_SIZE\":\"64\",
        \"FC_SIZE\":\"1024\"
      }
    }
  }" \
    localhost:"${TS_PORT}" training.TrainingService/Train

}

_temp=$(start_training "$dataset_id" "$version_hash")
job_id=$(echo -n "$_temp" | jq ".job_id")
echo "job_id is $job_id"

function check_job_status() {
    grpcurl -plaintext \
      -d "{\"job_id\": \"$1\"}" \
      localhost:"${TS_PORT}" training.TrainingService/GetTrainingStatus
}

job_status="unknown"
until [ $job_status == "\"failure\"" ] || [ $job_status == "\"succeed\"" ];
do
  echo "job $job_id is currently in $job_status status, check back in 5 seconds"
  sleep 5
  _temp=$(check_job_status "$job_id")
  job_status=$(echo -n "$_temp" | jq ".status")
done

grpcurl -plaintext \
  -d "{\"run_id\": \"$job_id\"}" \
  localhost:"${MS_PORT}" metadata_store.MetadataStoreService/GetRunStatus

grpcurl -plaintext \
  -d "{
    \"runId\": \"$job_id\",
    \"document\": \"You can have a certain #arrogance, and I think that's fine, but what you should never lose is the #respect for the others.\"
  }" \
  localhost:"${PS_PORT}" prediction.PredictionService/Predict

