# syntax=docker/dockerfile:1
FROM openjdk:11 AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY data-management/pom.xml data-management/pom.xml
COPY grpc-contract grpc-contract/
COPY metadata-store metadata-store/
RUN ./mvnw package -pl metadata-store -am -DskipTests

FROM openjdk:11 AS run
WORKDIR /app
COPY --from=builder /app/metadata-store/target/metadata-store-1.0-SNAPSHOT.jar ./metadata-store.jar

CMD ["java", "-jar", "metadata-store.jar"]
