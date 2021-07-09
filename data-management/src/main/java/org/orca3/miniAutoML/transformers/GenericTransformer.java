package org.orca3.miniAutoML.transformers;

import com.google.common.collect.ImmutableList;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.orca3.miniAutoML.DatasetPart;
import org.orca3.miniAutoML.FileInfo;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class GenericTransformer implements DatasetTransformer {

    @Override
    public List<FileInfo> compress(List<DatasetPart> parts, String datasetId, String versionHash, String bucketName, MinioClient minioClient) throws MinioException {
        String versionHashRoot = DatasetTransformer.getVersionHashRoot(datasetId, versionHash);
        for (int i = 0; i < parts.size(); i++) {
            int j = 0;
            for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(parts.get(i).getPathPrefix()).build())) {
                String newPath = Paths.get(versionHashRoot, String.format("part-%d-%d", i, j)).toString();
                try {
                    minioClient.copyObject(CopyObjectArgs.builder()
                            .bucket(bucketName).object(newPath)
                            .source(CopySource.builder().bucket(bucketName).object(r.get().objectName()).build())
                            .build());
                } catch (InvalidKeyException | IOException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                j++;
            }
        }
        return ImmutableList.of();
    }

    @Override
    public String ingest(String ingestBucket, String ingestPath, String datasetId, String commitId, String bucketName, MinioClient minioClient) throws MinioException {
        int i = 0;
        String commitRoot = DatasetTransformer.getCommitRoot(datasetId, commitId);
        for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).prefix(ingestPath).build())) {
            try {
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(bucketName).object(Paths.get(commitRoot, String.format("part-%d", i)).toString())
                        .source(CopySource.builder().bucket(bucketName).object(r.get().objectName()).build())
                        .build());
            } catch (InvalidKeyException | IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            i++;
        }
        return commitRoot;
    }
}
