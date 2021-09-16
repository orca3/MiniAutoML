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

public abstract class Tracker {
    protected final MemoryStore store;
    protected final DataManagementServiceGrpc.DataManagementServiceBlockingStub dmClient;
    protected final Logger logger;

    Tracker(MemoryStore store, ManagedChannel dmChannel, Logger logger) {
        this.store = store;
        this.dmClient = DataManagementServiceGrpc.newBlockingStub(dmChannel);
        this.logger = logger;
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
                        .finished(System.currentTimeMillis(), false, String.format("Dataset not available: %s.", ex.getMessage())));
                logger.warn(String.format("Failed to launch job %d.", jobId), ex);
            }
        }
    }
    public abstract boolean hasCapacity();

    protected abstract String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot);

    public abstract void updateContainerStatus();

    public abstract void shutdownAll();
}
