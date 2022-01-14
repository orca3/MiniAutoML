package org.orca3.miniAutoML.prediction;

import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.minio.DownloadObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PredictorConnectionManager {
    private final Map<String, List<ManagedChannel>> channels = new HashMap<>();
    private final Map<String, PredictorBackend> clients = new HashMap<>();
    private final String modelCachePath;
    private final MinioClient minioClient;
    private final Map<String, GetArtifactResponse> artifactCache;

    public PredictorConnectionManager(String modelCachePath, MinioClient minioClient) {
        this.modelCachePath = modelCachePath;
        this.minioClient = minioClient;
        this.artifactCache = Maps.newHashMap();
    }

    public boolean containsArtifact(String runId) {
        return artifactCache.containsKey(runId);
    }

    public GetArtifactResponse getArtifact(String runId) {
        return artifactCache.get(runId);
    }


    public void registerPredictor(String algorithm, Properties properties) {
        String host = properties.getProperty(String.format("predictors.%s.host", algorithm));
        int port = Integer.parseInt(properties.getProperty(String.format("predictors.%s.port", algorithm)));
        String predictorType = properties.getProperty(String.format("predictors.%s.techStack", algorithm));
        if (channels.containsKey(algorithm)) {
            channels.remove(algorithm).forEach(ManagedChannel::shutdown);
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        switch (predictorType) {
            case "torch":
                int managementPort = Integer.parseInt(properties.getProperty(String.format("predictors.%s.management-port", algorithm)));
                ManagedChannel managementChannel = ManagedChannelBuilder.forAddress(host, managementPort)
                        .usePlaintext().build();
                channels.put(algorithm, List.of(channel, managementChannel));
                clients.put(algorithm, new TorchGrpcPredictorBackend(channel, managementChannel, modelCachePath, minioClient));
                break;
            case "customGrpc":
            default:
                channels.put(algorithm, List.of(channel));
                clients.put(algorithm, new CustomGrpcPredictorBackend(channel, modelCachePath, minioClient));
                break;
        }
    }

    public boolean containsPredictor(String algorithm) {
        return clients.containsKey(algorithm);
    }

    public PredictorBackend getPredictor(String algorithm) {
        return clients.get(algorithm);
    }

    public void shutdown() {
        channels.values().forEach(channels -> channels.forEach(ManagedChannel::shutdown));
    }
}
