package org.orca3.miniAutoML.training.tracker;

import io.grpc.ManagedChannel;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class KubectlTracker extends Tracker {
    private final BackendConfig config;

    public KubectlTracker(MemoryStore store, Properties props, ManagedChannel dmChannel) {
        super(store, dmChannel, LoggerFactory.getLogger(KubectlTracker.class));
        this.config = new BackendConfig(props);

    }

    public static class BackendConfig {
        final String dmBucketName;
        final String minioAccessKey;
        final String minioSecretKey;
        final String minioHost;

        public BackendConfig(Properties properties) {
            this.dmBucketName = properties.getProperty("minio.dm.bucketName");
            this.minioAccessKey = properties.getProperty("minio.accessKey");
            this.minioSecretKey = properties.getProperty("minio.secretKey");
            this.minioHost = properties.getProperty("minio.host");
        }

    }

    @Override
    public boolean hasCapacity() {
        return true;
    }

    @Override
    protected String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        return null;
    }

    @Override
    public void updateContainerStatus() {

    }

    @Override
    public void shutdownAll() {

    }
}
