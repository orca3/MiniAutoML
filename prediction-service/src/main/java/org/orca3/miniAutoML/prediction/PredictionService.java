package org.orca3.miniAutoML.prediction;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
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
    private final PredictorConnectionManager predictorManager;

    public PredictionService(ManagedChannel msChannel, PredictorConnectionManager predictorManager, Config config) {
        this.config = config;
        this.msClient = MetadataStoreServiceGrpc.newBlockingStub(msChannel);
        this.predictorManager = predictorManager;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("Hello, Prediction Service!");
        Properties props = ServiceBase.getConfigProperties();
        Config config = new Config(props);
        ManagedChannel msChannel = ManagedChannelBuilder.forAddress(config.msHost, Integer.parseInt(config.msPort))
                .usePlaintext().build();
        MinioClient minioClient = MinioClient.builder()
                .endpoint(config.minioHost)
                .credentials(config.minioAccessKey, config.minioSecretKey)
                .build();
        PredictorConnectionManager connectionManager = new PredictorConnectionManager(config.modelCachePath, minioClient);
        for (String predictor : config.predictors) {
            connectionManager.registerPredictor(predictor, props);
        }
        PredictionService psService = new PredictionService(msChannel, connectionManager, config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), psService, () -> {
            // Graceful shutdown
            msChannel.shutdown();
            connectionManager.shutdown();
        });

    }

    @Override
    public void predict(PredictRequest request, StreamObserver<PredictResponse> responseObserver) {
        String runId = request.getRunId();
        GetArtifactResponse artifactInfo;

        if (predictorManager.containsArtifact(runId)) {
            artifactInfo = predictorManager.getArtifact(runId);
        } else {
            try {
                artifactInfo = msClient.getArtifact(GetArtifactRequest.newBuilder()
                        .setRunId(runId).build());
            } catch (Exception ex) {
                String msg = String.format("Cannot locate model artifact for runId %s.", runId);
                logger.error(msg, ex);
                responseObserver.onError(Status.NOT_FOUND.withDescription(msg).asException());
                return;
            }
        }

        PredictorBackend predictor;
        if (predictorManager.containsPredictor(artifactInfo.getAlgorithm())) {
            predictor = predictorManager.getPredictor(artifactInfo.getAlgorithm());
        } else {
            String msg = String.format("Algorithm %s doesn't have supporting predictor.", artifactInfo.getAlgorithm());
            logger.error(msg);
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(msg).asException());
            return;
        }
        try {
            predictor.downloadModel(runId, artifactInfo);
            String r = predictor.predict(artifactInfo, request.getDocument());
            responseObserver.onNext(PredictResponse.newBuilder().setResponse(r).build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            String msg = String.format("Prediction failed for algorithm %s: %s", artifactInfo.getAlgorithm(), ex.getMessage());
            logger.error(msg, ex);
            responseObserver.onError(Status.UNKNOWN
                    .withDescription(msg)
                    .asException());
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
        final String[] predictors;


        public Config(Properties properties) {
            this.msPort = properties.getProperty("ms.server.port");
            this.msHost = properties.getProperty("ms.server.host");
            this.minioAccessKey = properties.getProperty("minio.accessKey");
            this.minioSecretKey = properties.getProperty("minio.secretKey");
            this.minioHost = properties.getProperty("minio.host");
            this.serverPort = properties.getProperty("ps.server.port");
            this.modelCachePath = properties.getProperty("ps.server.modelCachePath");
            this.predictors = properties.getProperty("ps.enabledPredictors").split(",");

        }
    }
}
