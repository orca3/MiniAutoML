package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.transformers.IntentTextTransformer;

public class DatasetIngestion {

    public static String ingest(MinioClient minioClient, String datasetId, String commitId, DatasetType datasetType, String uri, String bucketName) {
        switch (datasetType) {
            case TEXT_INTENT:
                try {
                    return IntentTextTransformer.ingest(uri, datasetId, commitId, bucketName, minioClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            case GENERIC:
            default:
                return uri;
        }
    }
}
