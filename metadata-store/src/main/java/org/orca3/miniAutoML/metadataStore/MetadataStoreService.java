package org.orca3.miniAutoML.metadataStore;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.metadataStore.models.ArtifactInfo;
import org.orca3.miniAutoML.metadataStore.models.ArtifactRepo;
import org.orca3.miniAutoML.metadataStore.models.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class MetadataStoreService extends MetadataStoreServiceGrpc.MetadataStoreServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetadataStoreService.class);
    private final MemoryStore store;
    private Config config;

    public MetadataStoreService(MemoryStore store, Config config) {
        this.config = config;
        this.store = store;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        props.load(MetadataStoreService.class.getClassLoader().getResourceAsStream("config.properties"));
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

        MetadataStoreService msService = new MetadataStoreService(new MemoryStore(), config);
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
            this.minioBucketName = properties.getProperty("minio.bucketName");
            this.minioAccessKey = properties.getProperty("minio.accessKey");
            this.minioSecretKey = properties.getProperty("minio.secretKey");
            this.minioHost = properties.getProperty("minio.host");
            this.serverPort = properties.getProperty("server.port");
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
        ArtifactRepo repo;
        if (store.artifactRepos.containsKey(artifactName)) {
            repo = store.artifactRepos.get(artifactName);
        } else {
            repo = new ArtifactRepo(artifactName);
            store.artifactRepos.put(artifactName, repo);
        }
        version = Integer.toString(repo.getSeed());
        repo.artifacts.put(version, new ArtifactInfo(request.getArtifact()));
        responseObserver.onNext(CreateArtifactResponse.newBuilder()
                .setVersion(version)
                .setArtifact(request.getArtifact())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getArtifact(GetArtifactRequest request, StreamObserver<GetArtifactResponse> responseObserver) {
        String artifactName = request.getName();
        if (!store.artifactRepos.containsKey(artifactName)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Artifact %s doesn't exist", artifactName))
                    .asException());
        }
        ArtifactRepo repo = store.artifactRepos.get(artifactName);
        String version = request.getVersion();
        if (!repo.artifacts.containsKey(version)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Version %s of Artifact %s doesn't exist", version, artifactName))
                    .asException());
        }
        responseObserver.onNext(GetArtifactResponse.newBuilder()
                .setName(artifactName)
                .setVersion(version)
                .setArtifact(repo.artifacts.get(version).getFileInfo())
                .build());
        responseObserver.onCompleted();
    }
}
