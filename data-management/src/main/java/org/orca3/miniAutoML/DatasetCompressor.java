package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.transformers.IntentTextTransformer;

import java.util.List;

public class DatasetCompressor implements Runnable {
    private final MinioClient minioClient;
    private final DatasetType datasetType;
    private List<DatasetPart> parts;
    private final String versionHash;
    private final String bucketName;

    public DatasetCompressor(MinioClient minioClient, DatasetType datasetType, List<DatasetPart> parts, String versionHash, String bucketName) {
        this.minioClient = minioClient;
        this.datasetType = datasetType;
        this.parts = parts;
        this.versionHash = versionHash;
        this.bucketName = bucketName;
    }

    @Override
    public void run() {
        switch (datasetType) {
            case TEXT_INTENT:
                try {
                    IntentTextTransformer.compress(parts, versionHash, bucketName, minioClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case GENERIC:
            default:
                throw new NotImplementedException("Not implemented");
        }
    }
}
