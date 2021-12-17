package org.orca3.miniAutoML.prediction;

import com.google.common.collect.Maps;
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
import java.util.Map;

public class ModelManager {
    private final String modelCachePath;
    private final MinioClient minioClient;
    private final Map<String, String> algorithmCache;

    public ModelManager(String modelCachePath, MinioClient minioClient) {
        this.modelCachePath = modelCachePath;
        this.minioClient = minioClient;
        this.algorithmCache = Maps.newHashMap();
    }

    public boolean contains(String runId) {
        return algorithmCache.containsKey(runId);
    }

    public void set(String runId, GetArtifactResponse artifactResponse) {
        final String bucket = artifactResponse.getArtifact().getBucket();
        try {
            Path tempRoot = Paths.get(modelCachePath, runId);
            if (Files.notExists(tempRoot)) {
                Files.createDirectories(tempRoot);
            }
            for (Result<Item> item : minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(String.format("%s/", artifactResponse.getArtifact().getPath()))
                    .build())) {
                String objectName = item.get().objectName();
                String fileName = Paths.get(objectName).getFileName().toString();
                minioClient.downloadObject(DownloadObjectArgs.builder()
                        .bucket(bucket)
                        .object(item.get().objectName())
                        .filename(new File(tempRoot.toString(), fileName).getAbsolutePath())
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        algorithmCache.put(runId, artifactResponse.getAlgorithm());
    }

    public String getAlgorithm(String runId) {
        return algorithmCache.get(runId);
    }
}
