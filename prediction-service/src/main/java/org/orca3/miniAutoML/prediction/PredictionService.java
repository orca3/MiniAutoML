package org.orca3.miniAutoML.prediction;

import com.google.common.base.Strings;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import io.minio.MinioClient;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.metadataStore.GetArtifactRequest;
import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;
import org.orca3.miniAutoML.metadataStore.MetadataStoreServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class PredictionService extends PredictionServiceGrpc.PredictionServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);
    private final Config config;
    private final MetadataStoreServiceGrpc.MetadataStoreServiceBlockingStub msClient;
    private final ModelManager modelManager;
    private final PredictorConnectionManager connectionManager;

    public PredictionService(ManagedChannel msChannel, PredictorConnectionManager connectionManager, Config config) {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(config.minioHost)
                .credentials(config.minioAccessKey, config.minioSecretKey)
                .build();
        this.config = config;
        this.msClient = MetadataStoreServiceGrpc.newBlockingStub(msChannel);
        this.modelManager = new ModelManager(config.modelCachePath, minioClient);
        this.connectionManager = connectionManager;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("Hello, Prediction Service!");
        Properties props = ServiceBase.getConfigProperties();
        Config config = new Config(props);
        ManagedChannel msChannel = ManagedChannelBuilder.forAddress(config.msHost, Integer.parseInt(config.msPort))
                .usePlaintext().build();
        PredictorConnectionManager connectionManager = new PredictorConnectionManager();
        PredictionService psService = new PredictionService(msChannel, connectionManager, config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), psService, () -> {
            // Graceful shutdown
            msChannel.shutdown();
            connectionManager.shutdown();
        });

    }

    @Override
    public void predict(PredictRequest request, StreamObserver<PredictResponse> responseObserver) {
        String runId;
        String algorithm;
        runId = request.getRunId();
        if (modelManager.contains(runId)) {
            algorithm = modelManager.getAlgorithm(runId);
        } else {
            try {
                GetArtifactResponse artifactResponse = msClient.getArtifact(GetArtifactRequest.newBuilder()
                        .setRunId(runId).build());
                modelManager.set(runId, artifactResponse);
                algorithm = artifactResponse.getAlgorithm();
            } catch (Exception ex) {
                String msg = String.format("Cannot locate model artifact for runId %s.", runId);
                logger.error(msg, ex);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription(msg)
                        .asException());
                return;
            }
        }
        if (!connectionManager.contains(algorithm)) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription(String.format("Algorithm %s doesn't have supporting predictor.", algorithm))
                    .asException());
            return;
        }
        try {
            PredictorPredictResponse r = connectionManager.getClient(algorithm).predictorPredict(PredictorPredictRequest.newBuilder()
                    .setDocument(request.getDocument()).setRunId(runId).build());
            responseObserver.onNext(PredictResponse.newBuilder().setResponse(r.getResponse()).build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            String msg = String.format("Prediction failed for algorithm %s: %s", algorithm, ex.getMessage());
            logger.error(msg, ex);
            responseObserver.onError(Status.UNKNOWN
                    .withDescription(msg)
                    .asException());
        }

    }

    @Override
    public void registerPredictor(RegisterPredictorRequest request, StreamObserver<RegisterPredictorResponse> responseObserver) {
        if (Strings.isNullOrEmpty(request.getAlgorithm())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("algorithm is required.")
                    .asException());
            return;
        }
        if (Strings.isNullOrEmpty(request.getHost())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("host is required.")
                    .asException());
            return;
        }
        if (request.getPort() == 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("port is required and cannot be 0.")
                    .asException());
            return;
        }
        ManagedChannel channel;
        try {
            channel = ManagedChannelBuilder.forAddress(request.getHost(), request.getPort())
                    .usePlaintext().build();
            HealthCheckResponse a = HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.newBuilder().build());
            boolean up = HealthCheckResponse.ServingStatus.SERVING.equals(a.getStatus());
            if (up) {
                connectionManager.put(request.getAlgorithm(), request.getHost(), request.getPort());
            }
            responseObserver.onNext(RegisterPredictorResponse.newBuilder()
                    .setAlgorithm(request.getAlgorithm())
                    .setSuccess(up)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            String msg = String.format("Cannot connect with predictor on %s:%d", request.getHost(), request.getPort());
            logger.warn(msg, ex);
            responseObserver.onNext(RegisterPredictorResponse.newBuilder()
                    .setAlgorithm(request.getAlgorithm())
                    .setSuccess(false)
                    .build());
            responseObserver.onCompleted();
        }
    }

    static class Config {
        final String msPort;
        final String msHost;
        final String minioAccessKey;
        final String minioSecretKey;
        final String minioHost;
        final String serverPort;
        final String modelCachePath;


        public Config(Properties properties) {
            this.msPort = properties.getProperty("ms.server.port");
            this.msHost = properties.getProperty("ms.server.host");
            this.minioAccessKey = properties.getProperty("minio.accessKey");
            this.minioSecretKey = properties.getProperty("minio.secretKey");
            this.minioHost = properties.getProperty("minio.host");
            this.serverPort = properties.getProperty("ps.server.port");
            this.modelCachePath = properties.getProperty("ps.server.modelCachePath");
        }
    }
}
