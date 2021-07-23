package org.orca3.miniAutoML.transformers;

import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.orca3.miniAutoML.CommitInfo;
import org.orca3.miniAutoML.DatasetPart;
import org.orca3.miniAutoML.VersionedSnapshot;

import java.nio.file.Paths;
import java.util.List;

public interface DatasetTransformer {
    VersionedSnapshot compress(List<DatasetPart> parts, String datasetId, String versionHash, String bucketName, MinioClient minioClient) throws MinioException;

    CommitInfo.Builder ingest(String ingestBucket, String ingestPath, String datasetId, String commitId, String bucketName, MinioClient minioClient) throws MinioException;

    static String getCommitRoot(String datasetId, String commitId) {
        return Paths.get("dataset", datasetId, "commit", commitId).toString();
    }

    static String getVersionHashRoot(String datasetId, String versionHash) {
        return Paths.get("versionedDatasets", datasetId, versionHash).toString();
    }

}
