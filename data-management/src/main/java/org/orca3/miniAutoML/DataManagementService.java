package org.orca3.miniAutoML;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.orca3.miniAutoML.models.Commit;
import org.orca3.miniAutoML.models.Dataset;
import org.orca3.miniAutoML.models.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DataManagementService extends DataManagementServiceGrpc.DataManagementServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DataManagementService.class);
    private final MemoryStore store;
    private final Map<String, Future<?>> futures;
    private final ExecutorService threadPool;
    private final Config config;
    private final MinioClient minioClient;

    public DataManagementService(MemoryStore store, MinioClient minioClient, Config config) {
        this.store = store;
        this.threadPool = new ForkJoinPool(4);
        this.minioClient = minioClient;
        this.config = config;
        this.futures = Maps.newHashMap();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        props.load(DataManagementService.class.getClassLoader().getResourceAsStream("config.properties"));
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

        HealthStatusManager health = new HealthStatusManager();
        DataManagementService dmService = new DataManagementService(new MemoryStore(), minioClient, config);

        int port = Integer.parseInt(config.serverPort);
        final Server server = ServerBuilder.forPort(port)
                .addService(dmService)
                .addService(ProtoReflectionService.newInstance())
                .addService(health.getHealthService())
                .build()
                .start();
        System.out.println("Listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Start graceful shutdown
            server.shutdown();
            try {
                if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                    server.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                server.shutdownNow();
            }
        }));
        health.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
        server.awaitTermination();
    }

    @Override
    public void listDatasetCommits(DatasetPointer request, StreamObserver<DatasetSummary> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        DatasetSummary.Builder responseBuilder = DatasetSummary.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());
        for (Commit commit : dataset.commits.values()) {
            responseBuilder.addCommits(CommitInfo.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(commit.getCommitId())
                    .setCreatedAt(commit.getCreatedAt())
                    .build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listDatasets(ListQueryOptions request, StreamObserver<DatasetPointer> responseObserver) {
        for (String datasetId : store.datasets.keySet()) {
            responseObserver.onNext(DatasetPointer.newBuilder().setDatasetId(datasetId).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void createDataset(CreateDatasetRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        int datasetId = store.datasetIdSeed.incrementAndGet();
        Dataset dataset = new Dataset(datasetId, request.getName(), request.getDescription(), request.getDatasetType());
        store.datasets.put(Integer.toString(datasetId), dataset);
        int commitId = dataset.getNextCommitId();

        String commitUri = DatasetIngestion.ingest(minioClient, Integer.toString(datasetId), Integer.toString(commitId),
                request.getDatasetType(), request.getUri(), config.minioBucketName);
        dataset.commits.put(Integer.toString(commitId), new Commit(datasetId, commitId, commitUri, request.getTagsList()));

        responseObserver.onNext(DatasetVersionPointer.newBuilder()
                .setDatasetId(Integer.toString(datasetId))
                .setCommitId(Integer.toString(commitId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateDataset(CreateCommitRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        String commitId = Integer.toString(dataset.getNextCommitId());
        String commitUri = DatasetIngestion.ingest(minioClient, datasetId, commitId, dataset.getDatasetType(),
                request.getUri(), config.minioBucketName);
        dataset.commits.put(commitId, new Commit(datasetId, commitId, commitUri, request.getTagsList()));

        responseObserver.onNext(DatasetVersionPointer.newBuilder()
                .setDatasetId(datasetId)
                .setCommitId(commitId)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchVersionedDataset(DatasetQuery request, StreamObserver<DatasetVersionHash> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        String commitId;
        if (request.getCommitId().isEmpty()) {
            commitId = Integer.toString(dataset.getLastCommitId());
        } else {
            commitId = request.getCommitId();
            if (!dataset.commits.containsKey(commitId)) {
                responseObserver.onError(commitNotFoundException(datasetId, commitId));
                return;
            }
        }

        DatasetVersionHash.Builder responseBuilder = DatasetVersionHash.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());

        BitSet pickedCommits = new BitSet();
        List<DatasetPart> parts = Lists.newArrayList();
        for (int i = 1; i <= Integer.parseInt(commitId); i++) {
            Commit commit = dataset.commits.get(Integer.toString(i));
            boolean matched = true;
            for (Tag tag : request.getTagsList()) {
                if (commit.getTags().containsKey(tag.getTagKey())) {
                    matched = commit.getTags().get(tag.getTagKey()).equals(tag.getTagValue());
                } else {
                    matched = false;
                }
            }
            if (!matched) {
                continue;
            }
            pickedCommits.set(i);
            parts.add(DatasetPart.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(Integer.toString(i))
                    .setUri(commit.getUri())
                    .build());
        }
        String versionHash = String.format("hash%s", Base64.getEncoder().encodeToString(pickedCommits.toByteArray()));
        responseBuilder.setVersionHash(versionHash);

        String versionHashKey = MemoryStore.calculateVersionHashKey(datasetId, versionHash);
        store.versionHashRegistry.put(versionHashKey, VersionHashDataset.newBuilder()
                .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.RUNNING).build());
        futures.put(versionHash, threadPool.submit(new DatasetCompressor(minioClient, store, datasetId,
                dataset.getDatasetType(), parts, versionHash, config.minioBucketName)));


        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchDatasetByVersionHash(VersionHashQuery request, StreamObserver<VersionHashDataset> responseObserver) {
        String versionHashKey = MemoryStore.calculateVersionHashKey(request.getDatasetId(), request.getVersionHash());
        if (store.versionHashRegistry.containsKey(versionHashKey)) {
            responseObserver.onNext(store.versionHashRegistry.get(versionHashKey));
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("VersionHash %s for Dataset %s not found",
                            request.getVersionHash(), request.getDatasetId()))
                    .asException());
        }
    }

    @Override
    public void deleteDataset(DatasetPointer request, StreamObserver<Empty> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        store.datasets.remove(datasetId);
        responseObserver.onCompleted();
    }

    private Exception datasetNotFoundException(String datasetId) {
        return Status.NOT_FOUND
                .withDescription(String.format("Dataset %s not found", datasetId))
                .asException();
    }

    private Exception commitNotFoundException(String datasetId, String commitId) {
        return Status.NOT_FOUND
                .withDescription(String.format("There is no commit %s in dataset %s",
                        commitId, datasetId))
                .asException();
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
}
