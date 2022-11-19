# Run TorchServe Locally with a Sample Intent Model

The following instructions assume that your current working directory is in the
`predictor` subfolder. Please make sure Docker is running before proceeding.

## Step 1: Copy the sample intent model to a directory for TorchServe
```shell
mkdir -p /tmp/model_store/torchserving
cp sample_models/1/intent*.mar /tmp/model_store/torchserving
```

## Step 2: Run the TorchServe container
```shell
docker pull pytorch/torchserve:0.4.2-cpu
docker run --rm --shm-size=1g \
        --ulimit memlock=-1 \
        --ulimit stack=67108864 \
        -p8080:8080 \
        -p8081:8081 \
        -p8082:8082 \
        -p7070:7070 \
        -p7071:7071 \
        --mount type=bind,source=/tmp/model_store/torchserving,target=/tmp/models pytorch/torchserve:0.4.2-cpu torchserve --model-store=/tmp/models
``` 

## Step 3: Register model with TorchServe management API 
```shell
curl -X POST "http://localhost:8081/models?url=intent_80bf0da.mar&initial_workers=1&model_name=intent"
```
The response should look like
```shell
{
  "status": "Model \"intent\" Version: 1.0 registered with 1 initial workers"
}
```

## Step 4: Request predictions from the default version of the intent model
```shell
curl --location --request GET 'http://localhost:8080/predictions/intent' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```
The response should look like
```shell
{
  "predict_res": "timer"
}
```

## Step 5: Request predictions from a specific version of the intent model
This version is created at training time.
```shell
curl --location --request GET 'http://localhost:8080/predictions/intent/1.0' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```
The response should look like
```shell
{
  "predict_res": "timer"
}
```