package org.orca3.miniAutoML.metadataStore;

import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class MetadataStoreService extends MetadataStoreServiceGrpc.MetadataStoreServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetadataStoreService.class);
    private Config config;

    public MetadataStoreService(Config config) {
        this.config = config;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        props.load(MetadataStoreService.class.getClassLoader().getResourceAsStream("config.properties"));
        Config config = new Config(props);

        MetadataStoreService msService = new MetadataStoreService(config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), msService, () -> {});
    }

    static class Config {
        final String serverPort;

        public Config(Properties properties) {
            this.serverPort = properties.getProperty("server.port");
        }
    }

    @Override
    public void logExecutionStart(LogExecutionStartRequest request, StreamObserver<LogExecutionStartResponse> responseObserver) {
        super.logExecutionStart(request, responseObserver);
    }

    @Override
    public void logEpoch(LogEpochRequest request, StreamObserver<LogEpochResponse> responseObserver) {
        super.logEpoch(request, responseObserver);
    }

    @Override
    public void logExecutionEnd(LogExecutionEndRequest request, StreamObserver<LogExecutionEndResponse> responseObserver) {
        super.logExecutionEnd(request, responseObserver);
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
