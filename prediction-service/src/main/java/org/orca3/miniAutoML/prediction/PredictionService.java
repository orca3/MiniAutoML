package org.orca3.miniAutoML.prediction;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.metadataStore.MetadataStoreServiceGrpc;
import org.orca3.miniAutoML.training.TrainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class PredictionService extends PredictionServiceGrpc.PredictionServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);
    private final Config config;
    private final MetadataStoreServiceGrpc.MetadataStoreServiceBlockingStub msClient;

    public PredictionService(ManagedChannel msChannel, Config config) {
        this.config = config;
        this.msClient = MetadataStoreServiceGrpc.newBlockingStub(msChannel);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        props.load(PredictionService.class.getClassLoader().getResourceAsStream("config.properties"));
        Config config = new Config(props);
        ManagedChannel msChannel = ManagedChannelBuilder.forAddress(config.msHost, Integer.parseInt(config.msPort))
                .usePlaintext().build();

        PredictionService psService = new PredictionService(msChannel, config);
        ServiceBase.startService(Integer.parseInt(config.serverPort), psService, () -> {
            // Graceful shutdown
            msChannel.shutdown();
        });

    }

    @Override
    public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        responseObserver.onNext(EchoResponse.newBuilder().setMessage(request.getMessage()).build());
        responseObserver.onCompleted();
    }

    static class Config {
        final String msPort;
        final String msHost;
        final String serverPort;


        public Config(Properties properties) {
            this.msPort = properties.getProperty("ms.port");
            this.msHost = properties.getProperty("ms.host");
            this.serverPort = properties.getProperty("server.port");
        }
    }
}
