package org.orca3.miniAutoML;

import io.minio.MinioClient;
import org.apache.commons.lang3.NotImplementedException;
import org.orca3.miniAutoML.models.MemoryStore;
import org.orca3.miniAutoML.transformers.DatasetTransformer;
import org.orca3.miniAutoML.transformers.GenericTransformer;
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
        VersionHashDataset versionHashDataset;
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
            versionHashDataset = transformer.compress(datasetParts, datasetId, versionHash, bucketName, minioClient);
        } catch (Exception e) {
            store.versionHashRegistry.put(versionHashKey, VersionHashDataset.newBuilder()
                    .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.FAILED).build());
            throw new RuntimeException(e);
        }
        store.versionHashRegistry.put(versionHashKey, versionHashDataset);
    }
}
