``` Build image and run predict container
docker build -t orca3/intent-predictor -f Dockerfile .

docker run  --rm -d  -v {models}:/models -p 9090:5000 orca3/intent-predictor

example:
docker run  --rm -d  -v ~/workspace/cw/book/MiniAutoML/predictor:/models -p 9090:5000 orca3/intent-predictor
```

``` Test query predictor container. predictions/{model_id}
curl --location --request GET 'http://127.0.0.1:9090/predictions/1' \
--header 'Content-Type: text/plain' \
--data-raw 'make a 10 minute timer'
```