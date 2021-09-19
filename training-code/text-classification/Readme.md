## Set up local python env
```
conda create --name intent-pytorch python=3.7
conda activate intent-pytorch
conda install -c pytorch pytorch torchtext
conda install -c conda-forge minio

conda activate pytorch-p3.8
```
### Run training python script

`examples.csv` and `labels.csv` are the training dataset, we need to upload these training data to minio for running our training script below: (PS: The "Build and run docker image" section will cover how to upload data to minio server)

```
# single process training
python train.py
python train.py 0 1

# distributed training with two processes, first parameter is RANK, second is WORLD_SIZE. 
# RANK=0 is master
python train.py 0 2 
python trian.py 1 2
```

## Build and run docker image
Run `scripts/ts-000-build-trainer.sh` to build image, image name is `localhost:3000/orca3/intent-classification`.

Then start minio server by running `scripts/ms-001-start-minio.sh`. And upload the training dataset.

```
mc mb minio/mini-automl-dm
mc cp examples.csv minio/mini-automl-dm/versionedDatasets/1/hashDg==/examples.csv
mc cp labels.csv minio/mini-automl-dm/versionedDatasets/1/hashDg==/labels.csv
```
Last step, run distributed training with two docker instances. 

```
docker run --name trainer1 --hostname=trainer1 --rm --net orca3 -p 12356:12356 -e MASTER_ADDR="trainer1" -e WORLD_SIZE=2 -e RANK=0 -e MASTER_PORT=12356 -e MINIO_SERVER="minio:9000" -e TRAINING_DATA_PATH="versionedDatasets/1/hashDg==/" -it localhost:3000/orca3/intent-classification

docker run --name trainer2 --hostname=trainer2 --rm --net orca3 -e MASTER_ADDR="trainer1" -e WORLD_SIZE="2" -e RANK=1 -e MASTER_PORT=12356 -e MINIO_SERVER="minio:9000" -e TRAINING_DATA_PATH="versionedDatasets/1/hashDg==/" -it localhost:3000/orca3/intent-classification
```
