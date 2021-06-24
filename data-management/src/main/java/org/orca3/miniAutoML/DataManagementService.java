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
import org.orca3.miniAutoML.models.MemoryStore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DataManagementService extends DataManagementServiceGrpc.DataManagementServiceImplBase {
    private final MemoryStore store;

    public DataManagementService(MemoryStore store) {
        this.store = store;
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
        for (Commit commit: dataset.commits.values()) {
            responseBuilder.addCommits(CommitInfo.newBuilder()
                    .setDatasetId(datasetId)
                    .setCommitId(commit.getCommitId())
                    .setCreatedAt(commit.getCreatedAt())
                    .setCommitType(CommitType.valueOf(commit.getCommitType()))
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
        dataset.commits.put(Integer.toString(commitId),
                new Commit(datasetId, commitId, request.getUri(), CommitType.APPEND.name()));

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
        int commitId = dataset.getNextCommitId();
        dataset.commits.put(Integer.toString(commitId),
                new Commit(datasetId, commitId, request.getUri(), request.getCommitType().name()));

        responseObserver.onNext(DatasetVersionPointer.newBuilder()
                .setDatasetId(datasetId)
                .setCommitId(Integer.toString(commitId))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchVersionedDataset(DatasetVersionPointer request, StreamObserver<DatasetDetails> responseObserver) {
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

        DatasetDetails.Builder responseBuilder = DatasetDetails.newBuilder()
                .setDatasetId(datasetId)
                .setName(dataset.getName())
                .setDescription(dataset.getDescription())
                .setDatasetType(dataset.getDatasetType())
                .setLastUpdatedAt(dataset.getUpdatedAt());
        for (int i = 1; i <= Integer.parseInt(commitId); i ++) {
            Commit commit = dataset.commits.get(Integer.toString(i));
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
        DataManagementService dmService = new DataManagementService(new MemoryStore());
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
}
