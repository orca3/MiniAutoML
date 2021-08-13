package org.orca3.miniAutoML.metadataStore;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.metadataStore.models.ExecutionInfo;
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

        MetadataStoreService msService = new MetadataStoreService(new MemoryStore(), config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), msService, () -> {
        });
    }

    static class Config {
        final String serverPort;

        public Config(Properties properties) {
            this.serverPort = properties.getProperty("server.port");
        }
    }

    @Override
    public void logExecutionStart(LogExecutionStartRequest request, StreamObserver<LogExecutionStartResponse> responseObserver) {
        String runId = request.getRunId();
        if (store.executionInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription(String.format("Run %s already exists", runId))
                    .asException());
        }
        ExecutionInfo executionInfo = new ExecutionInfo(request.getStartTime(), runId, request.getRunName(), request.getTracing());
        store.executionInfoMap.put(request.getRunId(), executionInfo);

        responseObserver.onNext(LogExecutionStartResponse.newBuilder()
                .setStartTime(request.getStartTime())
                .setRunId(runId)
                .setRunName(request.getRunName())
                .setTracing(request.getTracing())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void logEpoch(LogEpochRequest request, StreamObserver<LogEpochResponse> responseObserver) {
        super.logEpoch(request, responseObserver);
    }

    @Override
    public void logExecutionEnd(LogExecutionEndRequest request, StreamObserver<LogExecutionEndResponse> responseObserver) {
        String runId = request.getRunId();
        if (!store.executionInfoMap.containsKey(runId)) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(String.format("Run %s doesn't exist", runId))
                    .asException());
        }
        ExecutionInfo ei = store.executionInfoMap.get(runId).endExecution(request.getEndTime(), request.getSuccess(), request.getMessage());
        responseObserver.onNext(LogExecutionEndResponse.newBuilder()
                .setStartTime(ei.getStartTime())
                .setEndTime(ei.getEndTime())
                .setSuccess(ei.isSuccess())
                .setMessage(ei.getMessage())
                .setRunId(ei.getRunId())
                .setRunName(ei.getRunName())
                .setTracing(ei.getTracing())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void createArtifact(CreateArtifactRequest request, StreamObserver<CreateArtifactResponse> responseObserver) {
        super.createArtifact(request, responseObserver);
    }

    @Override
    public void getArtifact(GetArtifactRequest request, StreamObserver<GetArtifactResponse> responseObserver) {
        super.getArtifact(request, responseObserver);
    }
}
