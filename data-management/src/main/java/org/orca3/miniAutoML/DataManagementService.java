package org.orca3.miniAutoML;

import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.models.Commit;
import org.orca3.miniAutoML.models.Dataset;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DataManagementService extends DataManagementServiceGrpc.DataManagementServiceImplBase {
    private final Jedis redis;
    private static final String DATASET_INDEX = "datasets";
    private static final String DATASET_KEY_FORMAT = "dataset:%s";
    private static final String COMMIT_KEY_FORMAT = "dataset:%s:commit:%s";
    private static final String DATASET_ID_SEED_KEY = "seed:dataset";
    private static final String DATASET_COMMIT_SEED_KEY = "latestCommit";

    public DataManagementService(Jedis redis) {
        this.redis = redis;
    }

    @Override
    public void listDatasetCommits(DatasetPointer request, StreamObserver<DatasetSummary> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!redis.sismember(DATASET_INDEX, datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        Dataset dataset = Dataset.fromRedisHash(redis.hgetAll(String.format(DATASET_KEY_FORMAT, datasetId)));
        DatasetSummary.Builder responseBuilder = DatasetSummary.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());
        long lastCommitId = Long.parseLong(redis.hget(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY));
        for (long commitId = 1; commitId <= lastCommitId; commitId++) {
            Commit commit = Commit.fromRedisHash(redis.hgetAll(String.format(COMMIT_KEY_FORMAT, datasetId, lastCommitId)));
            responseBuilder.addCommits(CommitInfo.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(Long.toString(commitId))
                    .setCreatedAt(commit.getCreatedAt())
                    .setCommitType(CommitType.valueOf(commit.getCommitType()))
                    .build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listDatasets(ListQueryOptions request, StreamObserver<DatasetPointer> responseObserver) {
        for (String datasetId : redis.smembers(DATASET_INDEX)) {
            responseObserver.onNext(DatasetPointer.newBuilder().setDatasetId(datasetId).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void createDataset(CreateDatasetRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        long datasetId = redis.incr(DATASET_ID_SEED_KEY);
        Dataset dataset = new Dataset(datasetId, request.getName(), request.getDescription(), request.getDatasetType());
        redis.hset(String.format(DATASET_KEY_FORMAT, datasetId), dataset.toRedisHash());
        long commitId = redis.hincrBy(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY, 1);
        Commit commit = new Commit(datasetId, commitId, request.getUri(), CommitType.APPEND.name());
        redis.hset(String.format(COMMIT_KEY_FORMAT, datasetId, commitId), commit.toRedisHash());
        redis.sadd(DATASET_INDEX, Long.toString(datasetId));

        responseObserver.onNext(DatasetVersionPointer.newBuilder()
                .setDatasetId(Long.toString(datasetId))
                .setCommitId(Long.toString(commitId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateDataset(CreateCommitRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!redis.sismember(DATASET_INDEX, datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        String lastCommitId = redis.hget(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY);
        String commitId = request.getParentCommitId();
        if (!lastCommitId.equals(commitId)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(String.format("Commit %s is not the latest commit in dataset %s",
                            commitId, datasetId))
                    .asException());
            return;
        }
        long newCommitId = redis.hincrBy(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY, 1);
        Commit commit = new Commit(datasetId, newCommitId, request.getUri(), request.getCommitType().name());
        redis.hset(String.format(COMMIT_KEY_FORMAT, datasetId, newCommitId), commit.toRedisHash());

        responseObserver.onNext(DatasetVersionPointer.newBuilder()
                .setDatasetId(datasetId)
                .setCommitId(Long.toString(newCommitId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void rollbackDataset(DatasetVersionPointer request, StreamObserver<DatasetVersionPointer> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!redis.sismember(DATASET_INDEX, datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        String commitId = request.getCommitId();
        if (!redis.exists(String.format(COMMIT_KEY_FORMAT, datasetId, commitId))) {
            responseObserver.onError(commitNotFoundException(datasetId, commitId));
            return;
        }
        long previousCommitId = Long.parseLong(redis.hget(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY));
        redis.hset(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY, commitId);
        long commitToDelete = Long.parseLong(commitId) + 1;
        while (commitToDelete <= previousCommitId) {
            redis.del(String.format(COMMIT_KEY_FORMAT, datasetId, commitToDelete));
            commitToDelete ++;
        }
    }

    @Override
    public void fetchVersionedDataset(DatasetVersionPointer request, StreamObserver<DatasetDetails> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!redis.sismember(DATASET_INDEX, datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        String commitId;
        if (request.getCommitId().isEmpty()) {
            commitId = redis.hget(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY);
        } else {
            commitId = request.getCommitId();
            if (!redis.exists(String.format(COMMIT_KEY_FORMAT, datasetId, commitId))) {
                responseObserver.onError(commitNotFoundException(datasetId, commitId));
                return;
            }
        }

        Dataset dataset = Dataset.fromRedisHash(redis.hgetAll(String.format(DATASET_KEY_FORMAT, datasetId)));
        DatasetDetails.Builder responseBuilder = DatasetDetails.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());
        for (long i = 1; i <= Long.parseLong(commitId); i ++) {
            Commit commit = Commit.fromRedisHash(redis.hgetAll(String.format(COMMIT_KEY_FORMAT, datasetId, i)));
            if (commit.getCommitType().equals(CommitType.OVERWRITE.name())) {
                responseBuilder.clearParts();
            }
            responseBuilder.addParts(DatasetPart.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(Long.toString(i))
                    .setUri(commit.getUri())
                    .build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteDataset(DatasetPointer request, StreamObserver<Empty> responseObserver) {
        String datasetId = request.getDatasetId();
        if (!redis.sismember(DATASET_INDEX, datasetId)) {
            responseObserver.onError(datasetNotFoundException(datasetId));
            return;
        }
        long lastCommitId = Long.parseLong(redis.hget(String.format(DATASET_KEY_FORMAT, datasetId), DATASET_COMMIT_SEED_KEY));
        redis.srem(DATASET_INDEX, datasetId);
        redis.del(String.format(DATASET_KEY_FORMAT, datasetId));
        for (int i = 1; i <= lastCommitId; i ++) {
            redis.del(String.format(COMMIT_KEY_FORMAT, datasetId, lastCommitId));
        }
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

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 51001;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("Usage: [port]");
                System.err.println("  port      The listen port. Defaults to " + port);
                System.exit(1);
            }
        }
        HealthStatusManager health = new HealthStatusManager();
        DataManagementService dmService = new DataManagementService(new Jedis("localhost", Protocol.DEFAULT_PORT));
        final Server server = ServerBuilder.forPort(port)
                .addService(dmService)
                .addService(ProtoReflectionService.newInstance())
                .addService(health.getHealthService())
                .build()
                .start();
        System.out.println("Listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Start graceful shutdown
            dmService.redis.close();
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
}
