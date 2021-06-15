package org.orca3.miniAutoML;

import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DataManagementService extends DataManagementServiceGrpc.DataManagementServiceImplBase {
    private static final Logger logger = Logger.getLogger(DataManagementService.class.getName());

    @Override
    public void listDatasetCommits(DatasetPointer request, StreamObserver<DatasetSummary> responseObserver) {
        super.listDatasetCommits(request, responseObserver);
    }

    @Override
    public void listDatasets(DatasetFilter request, StreamObserver<DatasetPointer> responseObserver) {
        super.listDatasets(request, responseObserver);
    }

    @Override
    public void createDataset(CreateDatasetRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        super.createDataset(request, responseObserver);
    }

    @Override
    public void updateDataset(CreateCommitRequest request, StreamObserver<DatasetVersionPointer> responseObserver) {
        super.updateDataset(request, responseObserver);
    }

    @Override
    public void rollbackDataset(DatasetVersionPointer request, StreamObserver<DatasetVersionPointer> responseObserver) {
        super.rollbackDataset(request, responseObserver);
    }

    @Override
    public void fetchLatestDataset(DatasetPointer request, StreamObserver<DatasetDetails> responseObserver) {
        super.fetchLatestDataset(request, responseObserver);
    }

    @Override
    public void fetchVersionedDataset(DatasetVersionPointer request, StreamObserver<DatasetDetails> responseObserver) {
        super.fetchVersionedDataset(request, responseObserver);
    }

    @Override
    public void deleteDataset(DatasetPointer request, StreamObserver<Empty> responseObserver) {
        super.deleteDataset(request, responseObserver);
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
        final Server server = ServerBuilder.forPort(port)
                .addService(new DataManagementService())
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
