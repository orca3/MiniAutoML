# Dataset Management (DM) Service 
Dataset management service is a sample Java (GRPC) webservice for demonstrating the design principles introduced in the chapter 4 of book - ``<<Deep learning Engineering>>``. 
This service is written in minimalism (for example persisting data in memory instead of a database) so the code is simple to read, and the local setup is easy. The only external dependency this service take is **Minio**, which we used to mimic cloud 
blob storage, such as `AWS S3` or `Azure blob`. Please make sure the minio client has been installed already by typing `mc --version` 

By reading these code, you will obtain a concrete feeling of how the dataset management design concept could be implemented.

## Function demo

See [demo.md](demo.md)

--------

## Build and play with DM locally

### Understand the config file
The DM server takes a few configuration items on startup. This can be found at [config.properties](src/main/resources/config.properties)
- `minio.bucketName`: The minio bucket name we want to use for DM service to store its file.
- `minio.accessKey` & `minio.secretKey`: The credential used to access the minio server.
- `minio.host`: The address of the minio server. 
- `server.port` The port number that this server listens to.

### Start dependency minio
This can be taken care of by our script [dm-001-start-minio.sh](../scripts/dm-001-start-minio.sh)

### Build and run using docker (recommended)
1. Modify config if needed. Set `minio.host` to `http://minio:9000` 
2. The [dockerfile](../services.dockerfile) in the root folder can be used to build the data-management service directly. Execute `docker build -t orca3/services:latest -f services.dockerfile .` in the root directly will build a docker image called `orca3/services` with `latest` tag.
3. Start the service using `docker run --name data-management --network orca3 --rm -d -p 6000:51001 orca3/services:latest data-management.jar`.
4. Now the service can be reached at `localhost:6000`. Try `grpcurl -plaintext localhost:6000 grpc.health.v1.Health/Check` or look at examples in [scripts](../scripts) folder to interact with the service
5. Everything above has the same effect as running our [dm-002-start-server.sh](../scripts/dm-002-start-server.sh)

### Build and run using java (for experienced Java developer)
1. Modify config if needed. Set `minio.host` to `http://localhost:9000`. Set `server.port` to an unoccupied port number.
2. Use maven to build the project and produce a runnable Jar `./mvnw clean package -pl data-management -am`
3. Run the jar using command `java -jar data-management/target/data-management-1.0-SNAPSHOT.jar`
4. Now the service can be reached at `localhost:51001`. Try open a new terminal tab and execute `grpcurl -plaintext localhost:51001 grpc.health.v1.Health/Check` or look at examples in [scripts](../scripts) folder to interact with the service 
