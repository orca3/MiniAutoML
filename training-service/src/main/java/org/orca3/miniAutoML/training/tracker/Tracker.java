package org.orca3.miniAutoML.training.tracker;

import io.grpc.ManagedChannel;
import org.orca3.miniAutoML.dataManagement.DataManagementServiceGrpc;
import org.orca3.miniAutoML.dataManagement.SnapshotState;
import org.orca3.miniAutoML.dataManagement.VersionQuery;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public abstract class Tracker<T extends Tracker.SharedConfig> {
    protected final MemoryStore store;
    protected final DataManagementServiceGrpc.DataManagementServiceBlockingStub dmClient;
    protected final Logger logger;
    protected final T config;

    Tracker(MemoryStore store, ManagedChannel dmChannel, Logger logger, T config) {
        this.store = store;
        this.dmClient = DataManagementServiceGrpc.newBlockingStub(dmChannel);
        this.logger = logger;
        this.config = config;
    }

    public void launchAll() {
        while (hasCapacity() && !store.jobQueue.isEmpty()) {
            int jobId = store.jobQueue.firstKey();
            TrainingJobMetadata metadata = store.jobQueue.get(jobId);
            try {
                VersionedSnapshot r = dmClient.fetchTrainingDataset(VersionQuery.newBuilder()
                        .setDatasetId(metadata.getDatasetId()).setVersionHash(metadata.getTrainDataVersionHash())
                        .build());
                if (r.getState() == SnapshotState.READY) {
                    store.jobQueue.remove(jobId);
                    launch(jobId, metadata, r);
                    store.launchingList.put(jobId, new ExecutedTrainingJob(System.currentTimeMillis(), metadata, ""));
                } else {
                    logger.info(String.format("Dataset %s of version hash %s is not ready yet. Current state: %s.",
                            metadata.getDatasetId(), metadata.getTrainDataVersionHash(), r.getState()));
                }
            } catch (Exception ex) {
                store.jobQueue.remove(jobId);
                store.finalizedJobs.put(jobId, new ExecutedTrainingJob(System.currentTimeMillis(), metadata, "")
                        .finished(System.currentTimeMillis(), false, ex.getMessage()));
                logger.warn(String.format("Failed to launch job %d.", jobId), ex);
            }
        }
    }

    protected Map<String, String> containerEnvVars(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        Map<String, String> envs = new HashMap<>();
        envs.put("JOB_ID", Integer.toString(jobId));
        envs.put("ALGORITHM_NAME", metadata.getAlgorithm());
        envs.put("METADATA_STORE_SERVER", config.metadataStoreHost);
        envs.put("TRAINING_DATASET_ID", metadata.getDatasetId());
        envs.put("TRAINING_DATASET_VERSION_HASH", metadata.getTrainDataVersionHash());
        envs.put("MODEL_BUCKET", config.metadataStoreBucketName);
        envs.put("MODEL_NAME", metadata.getOutputModelName());
        envs.put("MINIO_SERVER", config.minioHost);
        envs.put("MINIO_SERVER_ACCESS_KEY", config.minioAccessKey);
        envs.put("MINIO_SERVER_SECRET_KEY", config.minioSecretKey);
        envs.put("TRAINING_DATA_BUCKET", versionedSnapshot.getRoot().getBucket());
        envs.put("TRAINING_DATA_PATH", versionedSnapshot.getRoot().getPath());
        envs.putAll(metadata.getParametersMap());
        return envs;
    }

    public abstract boolean hasCapacity();

    protected abstract String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot);

    public abstract void updateContainerStatus();

    public abstract void shutdownAll();

    public static class SharedConfig {
        final String minioAccessKey;
        final String minioSecretKey;
        final String minioHost;
        final String metadataStoreBucketName;
        final String metadataStoreHost;

        SharedConfig(Properties props) {
            this.minioAccessKey = props.getProperty("minio.accessKey");
            this.minioSecretKey = props.getProperty("minio.secretKey");
            this.minioHost = props.getProperty("ts.trainer.minio.host");
            this.metadataStoreHost = props.getProperty("ts.trainer.ms.host");
            this.metadataStoreBucketName = props.getProperty("ms.minio.bucketName");
        }
    }
}
