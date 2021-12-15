package org.orca3.miniAutoML;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ServiceBase {
    private static final Logger logger = LoggerFactory.getLogger(ServiceBase.class);

    public static Properties getConfigProperties() throws IOException {
        String configLocation = Optional.ofNullable(System.getenv("APP_CONFIG")).orElse("config/config-jvm-docker.properties");
        logger.info(String.format("Reading config from %s on the file system (customizable by modifying environment variable APP_CONFIG)", configLocation));
        Properties props = new Properties();
        props.load(new FileInputStream(configLocation));
        return props;
    }

    public static void startService(int port, BindableService service, Runnable shutdownHook) throws IOException, InterruptedException {
        Logger logger = LoggerFactory.getLogger(service.getClass());
        HealthStatusManager health = new HealthStatusManager();
        final Server server = ServerBuilder.forPort(port)
                .addService(service)
                .addService(ProtoReflectionService.newInstance())
                .addService(health.getHealthService())
                .intercept(new GrpcInterceptor())
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

    static class GrpcInterceptor implements ServerInterceptor {

        @Override
        public <M, R> ServerCall.Listener<M> interceptCall(
                ServerCall<M, R> call, Metadata headers, ServerCallHandler<M, R> next) {
            GrpcServerCall<M, R> grpcServerCall = new GrpcServerCall<>(call);

            ServerCall.Listener<M> listener = next.startCall(grpcServerCall, headers);

            return new GrpcForwardingServerCallListener<>(call.getMethodDescriptor(), listener) {
                @Override
                public void onMessage(M message) {
                    logger.info("Method: {}, Message: {}", methodName, message);
                    super.onMessage(message);
                }
            };
        }


    }

    private static class GrpcServerCall<M, R> extends ServerCall<M, R> {

        ServerCall<M, R> serverCall;

        protected GrpcServerCall(ServerCall<M, R> serverCall) {
            this.serverCall = serverCall;
        }

        @Override
        public void request(int numMessages) {
            serverCall.request(numMessages);
        }

        @Override
        public void sendHeaders(Metadata headers) {
            serverCall.sendHeaders(headers);
        }

        @Override
        public void sendMessage(R message) {
            logger.info("Method: {}, Response: {}", serverCall.getMethodDescriptor().getFullMethodName(), message);
            serverCall.sendMessage(message);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            serverCall.close(status, trailers);
        }

        @Override
        public boolean isCancelled() {
            return serverCall.isCancelled();
        }

        @Override
        public MethodDescriptor<M, R> getMethodDescriptor() {
            return serverCall.getMethodDescriptor();
        }
    }

    private static class GrpcForwardingServerCallListener<M> extends io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener<M> {

        String methodName;

        protected GrpcForwardingServerCallListener(MethodDescriptor<?, ?> method, ServerCall.Listener<M> listener) {
            super(listener);
            methodName = method.getFullMethodName();
        }
    }
}

