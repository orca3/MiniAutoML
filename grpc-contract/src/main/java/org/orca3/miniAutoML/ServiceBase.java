package org.orca3.miniAutoML;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ServiceBase {
    public static void startService(int port, BindableService service, Runnable shutdownHook) throws IOException, InterruptedException {
        HealthStatusManager health = new HealthStatusManager();
        final Server server = ServerBuilder.forPort(port)
                .addService(service)
                .addService(ProtoReflectionService.newInstance())
                .addService(health.getHealthService())
                .build()
                .start();
        System.out.println("Listening on port " + port);
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
