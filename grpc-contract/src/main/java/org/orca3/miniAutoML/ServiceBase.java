package org.orca3.miniAutoML;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ServiceBase {
    private static final Logger logger = LoggerFactory.getLogger(ServiceBase.class);

    public static Properties getConfigProperties() throws IOException {
        String configLocation = Optional.ofNullable(System.getenv("APP_CONFIG")).orElse("config.properties");
        logger.info(String.format("Reading config from %s in the jar (customizable by modifying environment variable APP_CONFIG)", configLocation));
        Properties props = new Properties();
        props.load(ServiceBase.class.getClassLoader().getResourceAsStream(configLocation));
        return props;
    }

    public static void startService(int port, BindableService service, Runnable shutdownHook) throws IOException, InterruptedException {
        Logger logger = LoggerFactory.getLogger(service.getClass());
        HealthStatusManager health = new HealthStatusManager();
        final Server server = ServerBuilder.forPort(port)
                .addService(service)
                .addService(ProtoReflectionService.newInstance())
                .addService(health.getHealthService())
                .build()
                .start();
        logger.info("Listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            health.setStatus("", HealthCheckResponse.ServingStatus.NOT_SERVING);
            // Start graceful shutdown
            server.shutdown();
            shutdownHook.run();
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
