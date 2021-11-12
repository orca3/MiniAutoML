package org.orca3.miniAutoML.prediction;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.dataManagement.FileInfo;
import org.orca3.miniAutoML.metadataStore.GetArtifactRequest;
import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;
import org.orca3.miniAutoML.metadataStore.MetadataStoreServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class PredictionService extends PredictionServiceGrpc.PredictionServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);
    private final Config config;
    private final MetadataStoreServiceGrpc.MetadataStoreServiceBlockingStub msClient;
    private final ModelManager modelManager;
    private final PredictorConnectionManager connectionManager;

    public PredictionService(ManagedChannel msChannel, PredictorConnectionManager connectionManager, Config config) {
        this.config = config;
        this.msClient = MetadataStoreServiceGrpc.newBlockingStub(msChannel);
        this.modelManager = new ModelManager(config.modelCachePath);
        this.connectionManager = connectionManager;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("Hello, Prediction Service!");
        Properties props = ServiceBase.getConfigProperties();
        Config config = new Config(props);
        ManagedChannel msChannel = ManagedChannelBuilder.forAddress(config.msHost, Integer.parseInt(config.msPort))
                .usePlaintext().build();
        PredictorConnectionManager connectionManager = new PredictorConnectionManager();
        // FIXME: remove me once service registry is done
        connectionManager.put("intent-classification", "localhost", 50051);
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
        FileInfo artifactRoot = null;
        String algorithm;
        if (Strings.isNullOrEmpty(request.getRunId())) {
            if (Strings.isNullOrEmpty(request.getModelName()) || Strings.isNullOrEmpty(request.getVersion())) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Neither runId nor (modelName, version) were given.")
                        .asException());
                return;
            }
            try {
                GetArtifactResponse artifactResponse = msClient.getArtifact(GetArtifactRequest.newBuilder()
                        .setName(request.getModelName())
                        .setVersion(request.getVersion()).build());
                runId = artifactResponse.getRunId();
                artifactRoot = artifactResponse.getArtifact();
                algorithm = artifactResponse.getAlgorithm();
            } catch (Exception ex) {
                String msg = String.format("Cannot locate model artifact for name %s version %s.",
                        request.getModelName(), request.getVersion());
                logger.error(msg, ex);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription(msg)
                        .asException());
                return;
            }
        } else {
            runId = request.getRunId();
        }
        if (!modelManager.contains(runId)) {
            if (artifactRoot == null) {
                try {
                    GetArtifactResponse artifactResponse = msClient.getArtifact(GetArtifactRequest.newBuilder()
                                    .setName(runId).build());
                    artifactRoot = artifactResponse.getArtifact();
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
            modelManager.set(runId, artifactRoot);
            // FIXME
            algorithm = "intent-classification";
        } else {
            // FIXME
            algorithm = "intent-classification";
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

    static class Config {
        final String msPort;
        final String msHost;
        final String serverPort;
        final String modelCachePath;


        public Config(Properties properties) {
            this.msPort = properties.getProperty("ms.port");
            this.msHost = properties.getProperty("ms.host");
            this.serverPort = properties.getProperty("server.port");
            this.modelCachePath = properties.getProperty("server.modelCachePath");
        }
    }
}
