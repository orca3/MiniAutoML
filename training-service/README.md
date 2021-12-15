# Training Service (TS)
Training service is a sample Java (GRPC) webservice for demonstrating the design principles introduced in the chapter 5 of book - `<<bookname>>`.
This service is written in minimalism (for example persisting data in memory instead of a database) so the code is simple to read, and the local setup is easy.
There are multiple external dependencies required for this service to run
- **Minio**, which we used to mimic cloud blob storage, such as `AWS S3` or `Azure blob`. 
- **Data Management Service**, which we introduced in chapter 4.
- **Metadata Store Service**, which we introduced in chapter 5.
- Training container runtime, either **Docker** or **Kubernetes** (recommended)

By reading these code, you will learn how the training service design concept could be implemented.

## Function demo

See [demo.md](demo.md)

--------

## Build and play with TS locally

### Understand the config file
The TS server takes a few configuration items on startup. This can be found at [config.properties](src/main/resources/config.properties)
> For your convenience, we have provided another config file [config-kube.properties](src/main/resources/config-kube.properties) that has some out of box kubernetes configs
- `dm.host` & `dm.port`: The address of the data-management service.
- `server.port`: The port number that this server listens to.
- `trainer.minio.accessKey` & `trainer.minio.secretKey`: The credential used to access the minio server.
- `trainer.minio.host`: The training container launched by training service needs to talk to minio to access training data. This address changes based on the selected container runtime (`backend` config)
- `trainer.minio.metadataStore.bucketName`: Metadata store service's minio bucket name. The training container needs to write model artifacts into this bucket.
- `trainer.metadataStore.host`: Metadata store service address. The training container needs to communicate with metadata store service periodically.
- `backend`: This can be either `kubectl` or `docker`
  - **kubectl**: need to also provide `kubectl.configFile`, with which training service can talk to kubernetes cluster over API; `kubectl.namespace`, all training containers will be submitted in this namespace.
  - **docker**: need to also provide `docker.network`, all training containers will be connected to this docker network (so they can access Metadata Store Service)

### Start dependency minio
This can be taken care of by our script [ms-001-start-minio.sh](../scripts/ms-001-start-minio.sh)

### Start dependency Data Management Service
This can be taken care of by our script [dm-002-start-server.sh](../scripts/dm-002-start-server.sh)

### Start dependency Metadata Store Service
This can be taken care of by our script [ms-002-start-server.sh](../scripts/ms-002-start-server.sh)

### Prepare text classification trainer image
This can be taken care of by our script [ts-000-build-trainer.sh](../scripts/ts-000-build-trainer.sh). 
To make the trainer image available to both kubernetes and docker runtime, our script starts a local docker registry container on port 3000, then "upload" the trainer image to that local registry.
Rest assure there will be **no actual network traffic** involved in this process.

### Build and run using docker
1. Modify config if needed. Set `dm.host` to `data-management`.
2. The [dockerfile](../services.dockerfile) in the root folder can be used to build the training service directly. Execute `docker build -t orca3/services:latest -f services.dockerfile .` in the root directly will build a docker image called `orca3/services` with `latest` tag.
3. **Using docker training backend**: Start the service using `docker run --name training-service -v /var/run/docker.sock:/var/run/docker.sock --network orca3 --rm -d -p 5003:51001 orca3/services:latest training-service.jar`. Note we mount your `docker.sock` to the container so the training service container can talk to your docker server (to launch another training container). This is same as running our [ts-001-start-server.sh](../scripts/ts-001-start-server.sh)
4. **Using kubernetes training backend**: Start the service using `docker run --name training-service -v $HOME/.kube/config:/.kube/config --env APP_CONFIG=config-kube.properties --network orca3 --rm -d -p 5003:51001 orca3/services:latest training-service.jar`. Note we used a different config file [config-kube.properties](src/main/resources/config-kube.properties) and mounted your kube config to the container so the training container can talk to your kubernetes cluster (to launch other training pods). This is same as runinng our [ts-001-start-server-kube.sh](../scripts/ts-001-start-server-kube.sh)
5. Now the service can be reached at `localhost:5003`. Try `grpcurl -plaintext localhost:5003 grpc.health.v1.Health/Check` or look at examples in [scripts](../scripts) folder to interact with the service

### Build and run using java (for experienced Java developer)
1. Modify config if needed. Set `dm.host` to `localhost`. Set `dm.port` to `6000`. Set `kubectl.configFile` to your kube config. It is either the value of environment variable `KUBECONFIG` or by default `${HOME}/.kube/config`. Please use an absolute path (i.e in my case on a MacOS it is `/Users/robert.xue/.kube/config`)
2. Use maven to build the project and produce a runnable Jar `./mvnw clean package -pl training-service -am`.
3. **Using docker training backend**: Run the jar using command `java -jar training-service/target/training-service-1.0-SNAPSHOT.jar`
4. **Using kubernetes training backend**: Run the jar using command `APP_CONFIG=config-kube.properties java -jar training-service/target/training-service-1.0-SNAPSHOT.jar`
5. Now the service can be reached at `localhost:51001`. Try `grpcurl -plaintext localhost:51001 grpc.health.v1.Health/Check` or look at examples in [scripts](../scripts) folder to interact with the service

