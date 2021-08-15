package org.orca3.miniAutoML.dataManagement;

import io.minio.MinioClient;
import org.orca3.miniAutoML.dataManagement.models.Dataset;
import org.orca3.miniAutoML.dataManagement.models.MemoryStore;
import org.orca3.miniAutoML.dataManagement.transformers.DatasetTransformer;
import org.orca3.miniAutoML.dataManagement.transformers.GenericTransformer;
import org.orca3.miniAutoML.dataManagement.transformers.IntentTextTransformer;

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
        VersionedSnapshot versionHashDataset;
        DatasetTransformer transformer;
        Dataset dataset = store.datasets.get(datasetId);
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
            store.datasets.get(datasetId).versionHashRegistry.put(versionHash, VersionedSnapshot.newBuilder()
                    .setDatasetId(datasetId).setVersionHash(versionHash).setState(SnapshotState.FAILED).build());
            throw new RuntimeException(e);
        }
        dataset.versionHashRegistry.put(versionHash, versionHashDataset);
    }
}
