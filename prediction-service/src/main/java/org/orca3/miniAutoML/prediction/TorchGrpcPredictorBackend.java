package org.orca3.miniAutoML.prediction;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.minio.DownloadObjectArgs;
import io.minio.MinioClient;
import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;
import org.pytorch.serve.grpc.inference.InferenceAPIsServiceGrpc;
import org.pytorch.serve.grpc.inference.PredictionsRequest;
import org.pytorch.serve.grpc.management.ManagementAPIsServiceGrpc;
import org.pytorch.serve.grpc.management.ManagementResponse;
import org.pytorch.serve.grpc.management.RegisterModelRequest;
import org.pytorch.serve.grpc.management.ScaleWorkerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Set;

public class TorchGrpcPredictorBackend implements PredictorBackend {
    private static final Logger logger = LoggerFactory.getLogger(TorchGrpcPredictorBackend.class);
    private static final String MODEL_FILE_NAME_TEMPLATE = "model-%s.mar";
    private static final String TORCH_MODEL_MAR = "model.mar";
    private static final String TORCH_MODEL_NAME_TEMPLATE = "%s-%s";

    private final InferenceAPIsServiceGrpc.InferenceAPIsServiceBlockingStub stub;
    private final ManagementAPIsServiceGrpc.ManagementAPIsServiceBlockingStub managementStub;
    private final Set<String> downloadedModels;
    private final String modelCachePath;
    private final MinioClient minioClient;


    public TorchGrpcPredictorBackend(ManagedChannel predictorChannel, ManagedChannel managementChannel,
                                     String modelCachePath, MinioClient minioClient) {
        this.stub = InferenceAPIsServiceGrpc.newBlockingStub(predictorChannel);
        this.managementStub = ManagementAPIsServiceGrpc.newBlockingStub(managementChannel);
        this.modelCachePath = modelCachePath;
        this.minioClient = minioClient;
        this.downloadedModels = Sets.newHashSet();
    }

    @Override
    public void downloadModel(String runId, GetArtifactResponse artifactResponse) {
        if (downloadedModels.contains(runId)) {
            return;
        }
        final String bucket = artifactResponse.getArtifact().getBucket();
        try {
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucket)
                    .object(Paths.get(artifactResponse.getArtifact().getPath(), TORCH_MODEL_MAR).toString())
                    .filename(new File(modelCachePath, String.format(MODEL_FILE_NAME_TEMPLATE, runId)).getAbsolutePath())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        registerModel(artifactResponse);
        downloadedModels.add(runId);
    }

    @Override
    public void registerModel(GetArtifactResponse artifact) {
        String modelUrl = String.format(MODEL_FILE_NAME_TEMPLATE, artifact.getRunId());
        try {
            String torchModelName = String.format(TORCH_MODEL_NAME_TEMPLATE, artifact.getName(), artifact.getVersion());
            ManagementResponse r = managementStub.registerModel(RegisterModelRequest.newBuilder()
                    .setUrl(modelUrl)
                    .setModelName(torchModelName)
                    .build());
            logger.info(r.getMsg());
            managementStub.scaleWorker(ScaleWorkerRequest.newBuilder()
                    .setModelName(torchModelName)
                    .setMinWorker(1)
                    .build());
        } catch (Exception e) {
            logger.error("Failed to register model", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String predict(GetArtifactResponse artifact, String document) {
        return stub.predictions(PredictionsRequest.newBuilder()
                        .setModelName(String.format(TORCH_MODEL_NAME_TEMPLATE, artifact.getName(), artifact.getVersion()))
                        .putAllInput(ImmutableMap.of("data", ByteString.copyFrom(document, StandardCharsets.UTF_8)))
                        .build()).getPrediction()
                .toString(StandardCharsets.UTF_8);
    }
}
