# syntax=docker/dockerfile:1
# on local, try docker build -t orca3/services:latest -f services.dockerfile .
FROM openjdk:11 AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY data-management data-management
COPY grpc-contract grpc-contract
COPY metadata-store metadata-store
COPY training-service training-service
COPY prediction-service prediction-service
RUN ./mvnw package -DskipTests

FROM openjdk:11 AS run
WORKDIR /app
RUN GRPC_HEALTH_PROBE_VERSION=v0.3.1 && \
    wget -qO/bin/grpc_health_probe https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 && \
    chmod +x /bin/grpc_health_probe
COPY --from=builder /app/training-service/target/training-service-1.0-SNAPSHOT.jar ./training-service.jar
COPY --from=builder /app/data-management/target/data-management-1.0-SNAPSHOT.jar ./data-management.jar
COPY --from=builder /app/metadata-store/target/metadata-store-1.0-SNAPSHOT.jar ./metadata-store.jar
COPY --from=builder /app/prediction-service/target/prediction-service-1.0-SNAPSHOT.jar ./prediction-service.jar
COPY config ./config
ENV APP_CONFIG config/config-docker-docker.properties

ENTRYPOINT ["java", "-jar"]
