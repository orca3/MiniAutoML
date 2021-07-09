# Dataset Management (DM) Service 
Dataset management service is a sample Java (GRPC) webservice for demonstrating the design principles introduced in the chapter 4 of book - ``<<Deep learning Engineering>>``. 
We write this service in minimalism, so the code is simple to read, and the local setup is easy. The only external dependency this service take is MinIO, we use it to mimic cloud 
blob storage, such as (AWS S3 or Azure blob).

By reading these few thousand lines of code, you will obtain a concrete feeling of how the dataset management design concept
could be implemented.   

## Demo (GIF)
// TODO, @Robert
### Generic Dataset
1. create a dataset (Text)
2. get status (summary)
3. update a dataset 
4. get status (summary)
5. fetch training dataset by filter - time
6. fetch training dataset by filter - latest 3 commits

### Language Intent Dataset
1. create a dataset (Text)
2. get status (summary)
3. update a dataset
4. get status (summary)
5. fetch training dataset by filter - time
6. fetch training dataset by filter - latest 3 commits

## Setup (MinIO)
1. Install MinIO Server, by following https://docs.min.io/docs/minio-quickstart-guide.html
2. Pick a directory on your local to store DM data, such as ``~/data/dm/storage``
3. Start minIO server with default username and password by running: 
   
   ``minio server ~/data/dm/storage``
   
   You will see something like ```mc alias set myminio http://xxx.xxx.xxx.xxx:9000 minioadmin minioadmin``` in the stdout, 
   it means minio start a server named - "myminio", minio client use it as the target name to select which server to talks to. 
   It will start a minio file server on your local, you can also view it by visiting http://localhost:9000/, use "minioadmin" for access key and secret key.
   
4. Test the server by using minio client, 
   1. Install mc client by following https://docs.min.io/docs/minio-client-quickstart-guide.html
   2. Test to list files of minIO play environment by ``mc ls play``
   3. Test to cp file form your local to ``mc cp README.md myminio/dm/``

## Build 


## Run Test


## 
