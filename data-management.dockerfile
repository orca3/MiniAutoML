# syntax=docker/dockerfile:1
FROM openjdk:11 AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY data-management data-management/
COPY grpc-contract grpc-contract/
COPY metadata-store/pom.xml metadata-store/pom.xml
RUN ./mvnw package -pl data-management -am -DskipTests

FROM openjdk:11 AS run
WORKDIR /app
COPY --from=builder /app/data-management/target/data-management-1.0-SNAPSHOT.jar ./data-management.jar

CMD ["java", "-jar", "data-management.jar"]
