# syntax=docker/dockerfile:1
FROM openjdk:11 AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY data-management data-management/
COPY grpc-contract grpc-contract/
RUN ./mvnw package -DskipTests

FROM openjdk:11 AS run
WORKDIR /app
COPY --from=builder /app/data-management/target/data-management-1.0-SNAPSHOT.jar ./data-management.jar

CMD ["java", "-jar", "data-management.jar"]
