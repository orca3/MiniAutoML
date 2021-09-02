package org.orca3.miniAutoML.metadataStore.tracker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerTracker {
    final DockerClient dockerClient;
    final Map<Integer, String> jobIdTracker;
    private MemoryStore store;

    public DockerTracker(MemoryStore store) {
        this.store = store;
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        jobIdTracker = new HashMap<>();
    }

    public boolean hasCapacity() {
        return true;
    }

    public void launchAll() {
        while (hasCapacity() && !store.jobQueue.isEmpty()) {
            int jobId = store.jobQueue.firstKey();
            TrainingJobMetadata metadata = store.jobQueue.remove(jobId);
            launch(jobId, metadata);
            store.runningList.put(jobId, new ExecutedTrainingJob(System.currentTimeMillis(), null, false, metadata, ""));
        }
    }

    public String launch(int jobId, TrainingJobMetadata metadata) {
        String containerId = dockerClient.createContainerCmd(metadata.getAlgorithm())
                .withName(metadata.getName())
                .withAttachStdout(false)
                .withAttachStderr(false)
                .withCmd("server", "/data")
                .withEnv("MINIO_SERVER=minio:9000")
                .exec().getId();
        dockerClient.startContainerCmd(containerId).exec();
        jobIdTracker.put(jobId, containerId);
        return containerId;
    }

    public void run(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    @Nullable
    public String getJobStatus(int jobId) {
        if (jobIdTracker.containsKey(jobId)) {
            String containerId = jobIdTracker.get(jobId);
            InspectContainerResponse r = dockerClient.inspectContainerCmd(containerId).exec();
            return r.getState().getStatus();
        } else {
            return null;
        }
    }

    public void shutdownAll() {
        jobIdTracker.values().forEach(containerId -> {
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();
        });
    }
}
