package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.transformers.IntentTextTransformer;

import java.io.IOException;
import java.nio.file.Files;

public class DatasetCompressor implements Runnable {
    private final MinioClient minioClient;
    private final DatasetDetails details;
    private final String jobId;
    private final String bucketName;

    public DatasetCompressor(MinioClient minioClient, DatasetDetails details, String jobId, String bucketName) {
        this.minioClient = minioClient;
        this.details = details;
        this.jobId = jobId;
        this.bucketName = bucketName;
    }

    @Override
    public void run() {
        String tmpdir;
        try {
            tmpdir = Files.createTempDirectory(jobId).toFile().getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        switch (this.details.getDatasetType()) {
            case TEXT_INTENT:
                try {
                    IntentTextTransformer.compress(details, tmpdir, jobId, bucketName, minioClient);
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
