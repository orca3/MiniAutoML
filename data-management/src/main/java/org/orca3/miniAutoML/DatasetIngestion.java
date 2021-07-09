package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.transformers.DatasetTransformer;
import org.orca3.miniAutoML.transformers.GenericTransformer;
import org.orca3.miniAutoML.transformers.IntentTextTransformer;

public class DatasetIngestion {

    public static String ingest(MinioClient minioClient, String datasetId, String commitId, DatasetType datasetType, String ingestBucket, String ingestPath, String bucketName) {
        DatasetTransformer transformer;
        switch (datasetType) {
            case TEXT_INTENT:
                transformer = new IntentTextTransformer();
                break;
            case GENERIC:
            default:
                transformer = new GenericTransformer();
        }
        try {
            return transformer.ingest(ingestBucket, ingestPath, datasetId, commitId, bucketName, minioClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
