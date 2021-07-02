package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.models.MemoryStore;
import org.orca3.miniAutoML.transformers.IntentTextTransformer;

import java.util.List;

public class DatasetCompressor implements Runnable {
    private final MinioClient minioClient;
    private final String datasetId;
    private final DatasetType datasetType;
    private final MemoryStore store;
    private final List<DatasetPart> datasetParts;
    private final String versionHash;
    private final String bucketName;

    public DatasetCompressor(MinioClient minioClient, MemoryStore store, String datasetId,
                             DatasetType datasetType, List<DatasetPart> datasetParts,
                             String versionHash, String bucketName) {
        this.minioClient = minioClient;
        this.store = store;
        this.datasetId = datasetId;
        this.datasetType = datasetType;
        this.datasetParts = datasetParts;
        this.versionHash = versionHash;
        this.bucketName = bucketName;
    }

    @Override
    public void run() {
        String versionHashKey = MemoryStore.calculateVersionHashKey(datasetId, versionHash);
        List<FileInfo> versionHashParts;
        switch (datasetType) {
            case TEXT_INTENT:
                try {
                    versionHashParts = IntentTextTransformer.compress(datasetParts, datasetId, versionHash, bucketName, minioClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case GENERIC:
            default:
                store.versionHashRegistry.put(versionHashKey, VersionHashDataset.newBuilder()
                        .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.FAILED).build());
                throw new NotImplementedException("Not implemented");
        }
        store.versionHashRegistry.put(versionHashKey, VersionHashDataset.newBuilder()
                .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.READY)
                .addAllParts(versionHashParts).build());
    }
}
