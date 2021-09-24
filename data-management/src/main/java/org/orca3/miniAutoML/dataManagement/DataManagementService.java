package org.orca3.miniAutoML.dataManagement;

import com.google.common.collect.Lists;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.dataManagement.models.Dataset;
import org.orca3.miniAutoML.dataManagement.models.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class DataManagementService extends DataManagementServiceGrpc.DataManagementServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DataManagementService.class);
    private final MemoryStore store;
    private final ExecutorService threadPool;
    private final Config config;
    private final MinioClient minioClient;

    public DataManagementService(MemoryStore store, MinioClient minioClient, Config config) {
        this.store = store;
        this.threadPool = new ForkJoinPool(4);
        this.minioClient = minioClient;
        this.config = config;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
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

        DataManagementService dmService = new DataManagementService(new MemoryStore(), minioClient, config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), dmService, () -> {});
    }

    @Override
    public void getDatasetSummary(DatasetPointer request, StreamObserver<DatasetSummary> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        responseObserver.onNext(dataset.toDatasetSummary());
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
    public void createDataset(CreateDatasetRequest request, StreamObserver<DatasetSummary> responseObserver) {
        String datasetId = Integer.toString(store.datasetIdSeed.incrementAndGet());
        Dataset dataset = new Dataset(datasetId, request.getName(), request.getDescription(), request.getDatasetType());
        store.datasets.put(datasetId, dataset);
        String commitId = Integer.toString(dataset.getNextCommitId());

        CommitInfo.Builder builder = DatasetIngestion.ingest(minioClient, datasetId, commitId,
                request.getDatasetType(), request.getBucket(), request.getPath(), config.minioBucketName);
        dataset.commits.put(commitId, builder
                .setCommitMessage("Initial commit")
                .addAllTags(request.getTagsList()).build());

        responseObserver.onNext(dataset.toDatasetSummary());
        responseObserver.onCompleted();
    }

    @Override
    public void updateDataset(CreateCommitRequest request, StreamObserver<DatasetSummary> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        String commitId = Integer.toString(dataset.getNextCommitId());
        CommitInfo.Builder builder = DatasetIngestion.ingest(minioClient, datasetId, commitId, dataset.getDatasetType(),
                request.getBucket(), request.getPath(), config.minioBucketName);
        dataset.commits.put(commitId, builder
                .setCommitMessage(request.getCommitMessage())
                .addAllTags(request.getTagsList()).build());

        responseObserver.onNext(dataset.toDatasetSummary());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareTrainingDataset(DatasetQuery request, StreamObserver<SnapshotVersion> responseObserver) {
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

        SnapshotVersion.Builder responseBuilder = SnapshotVersion.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());

        BitSet pickedCommits = new BitSet();
        List<DatasetPart> parts = Lists.newArrayList();
        List<CommitInfo> commitInfoList = Lists.newLinkedList();
        for (int i = 1; i <= Integer.parseInt(commitId); i++) {
            CommitInfo commit = dataset.commits.get(Integer.toString(i));
            boolean matched = true;
            for (Tag tag : request.getTagsList()) {
                matched &= commit.getTagsList().stream().anyMatch(k -> k.equals(tag));
            }
            if (!matched) {
                continue;
            }
            pickedCommits.set(i);
            commitInfoList.add(commit);
            parts.add(DatasetPart.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(Integer.toString(i))
                    .setBucket(config.minioBucketName)
                    .setPathPrefix(commit.getPath())
                    .build());
        }
        String versionHash = String.format("hash%s", Base64.getEncoder().encodeToString(pickedCommits.toByteArray()));
        responseBuilder.addAllCommits(commitInfoList).setVersionHash(versionHash);

        if (!dataset.versionHashRegistry.containsKey(versionHash)) {
            dataset.versionHashRegistry.put(versionHash, VersionedSnapshot.newBuilder()
                    .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.RUNNING).build());
            threadPool.submit(new DatasetCompressor(minioClient, store, datasetId,
                    dataset.getDatasetType(), parts, versionHash, config.minioBucketName));
        }


        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchTrainingDataset(VersionQuery request, StreamObserver<VersionedSnapshot> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!store.datasets.containsKey(datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = store.datasets.get(datasetId);
        if (dataset.versionHashRegistry.containsKey(request.getVersionHash())) {
            responseObserver.onNext(dataset.versionHashRegistry.get(request.getVersionHash()));
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
        responseObserver.onNext(Empty.newBuilder().build());
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
