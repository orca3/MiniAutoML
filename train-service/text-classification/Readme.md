## Set up local python env
conda create --name intent-pytorch python=3.7
conda activate intent-pytorch
conda install -c pytorch pytorch torchtext
conda install -c conda-forge minio

## build and run docker image
docker build -t intent-classification .
docker run  -e MINIO_SERVER="172.17.0.3:9000" intent-classification
<TODO>, currently minIO container access will fail
