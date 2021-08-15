package org.orca3.miniAutoML.dataManagement;

import io.minio.MinioClient;
import org.orca3.miniAutoML.dataManagement.transformers.DatasetTransformer;
import org.orca3.miniAutoML.dataManagement.transformers.GenericTransformer;
import org.orca3.miniAutoML.dataManagement.transformers.IntentTextTransformer;

public class DatasetIngestion {

    public static CommitInfo.Builder ingest(MinioClient minioClient, String datasetId, String commitId, DatasetType datasetType, String ingestBucket, String ingestPath, String bucketName) {
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
