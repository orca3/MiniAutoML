package org.orca3.miniAutoML.dataManagement.transformers;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.orca3.miniAutoML.dataManagement.CommitInfo;
import org.orca3.miniAutoML.dataManagement.DatasetPart;
import org.orca3.miniAutoML.dataManagement.SnapshotState;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class GenericTransformer implements DatasetTransformer {

    @Override
    public VersionedSnapshot compress(List<DatasetPart> parts, String datasetId, String versionHash, String bucketName, MinioClient minioClient) throws MinioException {
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
        return VersionedSnapshot.newBuilder()
                .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.READY)
                .build();
    }

    @Override
    public CommitInfo.Builder ingest(String ingestBucket, String ingestPath, String datasetId, String commitId, String bucketName, MinioClient minioClient) throws MinioException {
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
        return CommitInfo.newBuilder()
                .setDatasetId(datasetId)
                .setCommitId(commitId)
                .setCreatedAt(ISO_INSTANT.format(Instant.now()))
                .setPath(commitRoot);
    }
}
