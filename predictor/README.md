## Setup self-built predictor container

``` Build image and run predict container
cd predictor
docker build -t orca3/intent-predictor -f Dockerfile .

docker run  --rm -d  -v {models_folder}:/models -p 9090:5000 orca3/intent-predictor
```
Example to run intent-predictor locally.
```
docker build -t orca3/intent-predictor -f Dockerfile .
mkdir -p /tmp/model_store/predictor
cp -r sample_models/* /tmp/model_store/predictor
docker run  --rm -d  -v /tmp/model_store/predictor:/models -p 9090:5000 orca3/intent-predictor
```
Send intent prediction request to the intent predictor.
``` Test query predictor container. predictions/{model_id}
// model_id = 1
curl --location --request GET 'http://127.0.0.1:9090/predictions/1' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```

## Run torch serving with intent model locally.
```
// create model store directory for torch serving and copy models
mkdir -p /tmp/model_store/torchserving
cp sample_models/1/intent*.mar /tmp/model_store/torchserving

// run the torch serving container
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

// register model (intent_80bf0da) through torchserving management api
curl -X POST  "http://localhost:8081/models?url=intent_80bf0da.mar&initial_workers=1&model_name=intent"
```

Query intent model in torch serving with default version.
```
curl --location --request GET 'http://localhost:8080/predictions/intent' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```

Query intent model in torch serving with specified version, this version is created at training time.
```
curl --location --request GET 'http://localhost:8080/predictions/intent/1.0' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```