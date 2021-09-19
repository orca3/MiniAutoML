## Set up local python env
conda create --name intent-pytorch python=3.7
conda activate intent-pytorch
conda install -c pytorch pytorch torchtext
conda install -c conda-forge minio

## build and run docker image
docker build -t intent-classification .
docker run  -e MINIO_SERVER="172.17.0.3:9000" intent-classification
<TODO>, currently minIO container access will fail

docker run --name trainer1 --hostname=trainer1 -p 12356:12356 -e MASTER_ADDR="trainer1" -e WORLD_SIZE=2 -e RANK=0 -e MASTER_PORT=12356 -it localhost:3000/orca3/intent-classification

docker run --name trainer2 --hostname=trainer2  -e MASTER_ADDR="trainer1" -e WORLD_SIZE="2" -e RANK=1 -e MASTER_PORT=12356 -it localhost:3000/orca3/intent-classification
