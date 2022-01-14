package org.orca3.miniAutoML.prediction;

import com.google.common.collect.Sets;
import io.grpc.ManagedChannel;
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
import java.util.Set;

public class CustomGrpcPredictorBackend implements PredictorBackend {
    private final PredictorGrpc.PredictorBlockingStub stub;
    private final String modelCachePath;
    private final MinioClient minioClient;
    private final Set<String> downloadedModels;

    public CustomGrpcPredictorBackend(ManagedChannel channel, String modelCachePath, MinioClient minioClient) {
        stub = PredictorGrpc.newBlockingStub(channel);
        this.modelCachePath = modelCachePath;
        this.minioClient = minioClient;
        this.downloadedModels = Sets.newHashSet();
    }

    @Override
    public void registerModel(GetArtifactResponse artifact) {
        return;
    }

    @Override
    public void downloadModel(String runId, GetArtifactResponse artifactResponse) {
        if (downloadedModels.contains(runId)) {
            return;
        }
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
        registerModel(artifactResponse);
        downloadedModels.add(runId);
    }

    @Override
    public String predict(GetArtifactResponse artifact, String document) {
        return stub.predictorPredict(PredictorPredictRequest.newBuilder()
                .setDocument(document).setRunId(artifact.getRunId()).build()).getResponse();
    }
}
