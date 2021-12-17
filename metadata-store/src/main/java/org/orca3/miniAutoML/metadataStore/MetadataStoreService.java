package org.orca3.miniAutoML.metadataStore;

import com.google.common.base.Strings;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.dataManagement.FileInfo;
import org.orca3.miniAutoML.metadataStore.models.ArtifactInfo;
import org.orca3.miniAutoML.metadataStore.models.ArtifactRepo;
import org.orca3.miniAutoML.metadataStore.models.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

public class MetadataStoreService extends MetadataStoreServiceGrpc.MetadataStoreServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetadataStoreService.class);
    private final MemoryStore store;
    private final MinioClient minioClient;
    private final Config config;

    public MetadataStoreService(MemoryStore store, MinioClient minioClient, Config config) {
        this.minioClient = minioClient;
        this.config = config;
        this.store = store;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("Hello, Metadata Store Service!");
        Properties props = ServiceBase.getConfigProperties();
        Config config = new Config(props);
        MinioClient minioClient = MinioClient.builder()
                .endpoint(config.minioHost)
                .credentials(config.minioAccessKey, config.minioSecretKey)
                .build();
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.minioBucketName).build());
            if (!found) {
                logger.info("Creating bucket '{}'.", config.minioBucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.minioBucketName).build());
            } else {
                logger.info("Bucket '{}' already exists.", config.minioBucketName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        MetadataStoreService msService = new MetadataStoreService(new MemoryStore(), minioClient, config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), msService, () -> {
        });
    }

    static class Config {
        final String minioBucketName;
        final String minioAccessKey;
        final String minioSecretKey;
        final String minioHost;
        final String serverPort;

        public Config(Properties properties) {
            this.minioBucketName = properties.getProperty("ms.minio.bucketName");
            this.minioAccessKey = properties.getProperty("minio.accessKey");
            this.minioSecretKey = properties.getProperty("minio.secretKey");
            this.minioHost = properties.getProperty("minio.host");
            this.serverPort = properties.getProperty("ms.server.port");
        }
    }

    @Override
    public void logRunStart(LogRunStartRequest request, StreamObserver<LogRunStartResponse> responseObserver) {
        String runId = request.getRunId();
        if (store.runInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription(String.format("Run %s already exists", runId))
                    .asException());
        }
        RunInfo runInfo = RunInfo.newBuilder()
                .setStartTime(request.getStartTime())
                .setRunId(request.getRunId())
                .setRunName(request.getRunName())
                .setTracing(request.getTracing())
                .setSuccess(false)
                .setMessage("Running")
                .build();
        store.runInfoMap.put(request.getRunId(), runInfo);

        responseObserver.onNext(LogRunStartResponse.newBuilder()
                .setRunInfo(runInfo)
                .setBucket(config.minioBucketName)
                .setPath(String.format("run_%s", runId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void logEpoch(LogEpochRequest request, StreamObserver<LogEpochResponse> responseObserver) {
        String runId = request.getEpochInfo().getRunId();
        if (!store.runInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Run %s doesn't exist", runId))
                    .asException());
        }
        RunInfo ri = store.runInfoMap.get(runId);
        String epochId = request.getEpochInfo().getEpochId();
        if (ri.getEpochsMap().containsKey(epochId)) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription(String.format("Epoch %s in Run %s already exists", epochId, runId))
                    .asException());
        }
        store.runInfoMap.put(runId, RunInfo.newBuilder().mergeFrom(ri)
                .putEpochs(request.getEpochInfo().getEpochId(), request.getEpochInfo())
                .build());

        responseObserver.onNext(LogEpochResponse.newBuilder()
                .setEpochInfo(request.getEpochInfo())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void logRunEnd(LogRunEndRequest request, StreamObserver<LogRunEndResponse> responseObserver) {
        String runId = request.getRunId();
        if (!store.runInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Run %s doesn't exist", runId))
                    .asException());
        }
        RunInfo ri = RunInfo.newBuilder().mergeFrom(store.runInfoMap.get(runId))
                .setEndTime(request.getEndTime())
                .setSuccess(request.getSuccess())
                .setMessage(request.getMessage())
                .build();
        store.runInfoMap.put(runId, ri);
        responseObserver.onNext(LogRunEndResponse.newBuilder()
                .setRunInfo(ri)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRunStatus(GetRunStatusRequest request, StreamObserver<GetRunStatusResponse> responseObserver) {
        String runId = request.getRunId();
        if (!store.runInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Run %s doesn't exist", runId))
                    .asException());
        }
        RunInfo ri = store.runInfoMap.get(runId);
        responseObserver.onNext(GetRunStatusResponse.newBuilder()
                .setRunInfo(ri)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void createArtifact(CreateArtifactRequest request, StreamObserver<CreateArtifactResponse> responseObserver) {
        String artifactName = request.getArtifact().getName();
        String version;
        ArtifactRepo repo = store.getRepo(artifactName);
        version = Integer.toString(repo.getSeed());
        FileInfo destination = FileInfo.newBuilder().setName(artifactName)
                .setBucket(config.minioBucketName)
                .setPath(artifactName)
                .build();
        String artifactBucket = request.getArtifact().getBucket();
        String artifactFolder = request.getArtifact().getPath();
        try {
            Iterable<Result<Item>> artifacts = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(artifactBucket).prefix(String.format("%s/", artifactFolder)).build());
            for (Result<Item> artifact : artifacts) {
                String artifactPrefix = artifact.get().objectName();
                String fileName = Paths.get(artifactPrefix).getFileName().toString();
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(config.minioBucketName)
                        .object(Paths.get(artifactName, fileName).toString())
                        .source(CopySource.builder().bucket(artifactBucket)
                                .object(artifactPrefix).build())
                        .build());
            }
        } catch (InvalidKeyException | IOException | NoSuchAlgorithmException | MinioException e) {
            throw new RuntimeException(e);
        }

        store.put(artifactName, version, new ArtifactInfo(destination, request.getRunId(), artifactName, version,
                request.getAlgorithm()));
        responseObserver.onNext(CreateArtifactResponse.newBuilder()
                .setVersion(version)
                .setRunId(request.getRunId())
                .setArtifact(destination)
                .setName(artifactName)
                .setAlgorithm(request.getAlgorithm())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getArtifact(GetArtifactRequest request, StreamObserver<GetArtifactResponse> responseObserver) {
        if (Strings.isNullOrEmpty(request.getRunId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("RunId required.")
                    .asException());
            return;
        }
        String runId = request.getRunId();
        if (!store.runIdLookup.containsKey(runId)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Artifact with runId %s doesn't exist", runId))
                    .asException());
            return;
        }
        ArtifactInfo artifact = store.runIdLookup.get(runId);
        responseObserver.onNext(GetArtifactResponse.newBuilder()
                .setName(artifact.getArtifactName())
                .setVersion(artifact.getVersion())
                .setRunId(artifact.getRunId())
                .setArtifact(artifact.getFileInfo())
                .setAlgorithm(artifact.getAlgorithm())
                .build());
        responseObserver.onCompleted();
    }
}
