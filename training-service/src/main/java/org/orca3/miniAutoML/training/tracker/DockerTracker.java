package org.orca3.miniAutoML.training.tracker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.orca3.miniAutoML.dataManagement.DataManagementServiceGrpc;
import org.orca3.miniAutoML.dataManagement.SnapshotState;
import org.orca3.miniAutoML.dataManagement.VersionQuery;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerTracker extends Tracker<DockerTracker.BackendConfig> {
    final DockerClient dockerClient;
    final Map<Integer, String> jobIdTracker;

    public DockerTracker(MemoryStore store, Properties props, ManagedChannel dmChannel) {
        super(store, dmChannel, LoggerFactory.getLogger(DockerTracker.class), new BackendConfig(props));
        DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .sslConfig(clientConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient);
        jobIdTracker = new HashMap<>();
    }

    @Override
    public boolean hasCapacity() {
        return store.launchingList.size() + store.runningList.size() == 0;
    }

    @Override
    public void updateContainerStatus() {
        Set<Integer> launchingJobs = store.launchingList.keySet();
        Set<Integer> runningJobs = store.runningList.keySet();
        logger.info(String.format("Scanning %d jobs", launchingJobs.size() + runningJobs.size()));
        for (Integer jobId : launchingJobs) {
            String containerId = jobIdTracker.get(jobId);
            InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
            String containerStatus = state.getStatus();
            if (containerStatus == null) {
                continue;
            }
            switch (containerStatus) {
                case "created":
                case "restarting":
                case "paused":
                    // launching, no op
                    break;
                case "running":
                case "removing":
                    // move to running
                    ExecutedTrainingJob running = store.launchingList.remove(jobId);
                    store.runningList.put(jobId, running);
                    break;
                case "exited":
                case "dead":
                    ExecutedTrainingJob stopped = store.launchingList.remove(jobId);
                    long existCode = state.getExitCodeLong() == null ? -1 : state.getExitCodeLong();
                    store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), existCode == 0,
                            String.format("Exit code %d", existCode)));
            }
        }
        for (Integer jobId : runningJobs) {
            String containerId = jobIdTracker.get(jobId);
            InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
            String containerStatus = state.getStatus();
            if (containerStatus == null) {
                continue;
            }
            switch (containerStatus) {
                case "created":
                case "restarting":
                case "paused":
                    // launching
                    ExecutedTrainingJob launching = store.runningList.remove(jobId);
                    store.launchingList.put(jobId, launching);
                    break;
                case "running":
                case "removing":
                    // move to running, no op
                    break;
                case "exited":
                case "dead":
                    ExecutedTrainingJob stopped = store.runningList.remove(jobId);
                    long existCode = state.getExitCodeLong() == null ? -1 : state.getExitCodeLong();
                    store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), existCode == 0,
                            String.format("Exit code %d", existCode)));
            }
        }
    }

    @Override
    protected String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        Map<String, String> envs =containerEnvVars(metadata, versionedSnapshot);
        List<String> envStrings = envs.entrySet().stream()
                .map(kvp -> String.format("%s=%s", kvp.getKey(), kvp.getValue()))
                .collect(Collectors.toList());
        String containerId = dockerClient.createContainerCmd(algorithmToImage(metadata.getAlgorithm()))
                .withName(String.format("%d-%d-%s", jobId, System.currentTimeMillis(), metadata.getName()))
                .withAttachStdout(false)
                .withAttachStderr(false)
                .withEnv(envStrings)
                .withHostConfig(HostConfig.newHostConfig().withNetworkMode(config.network))
                .exec().getId();
        dockerClient.startContainerCmd(containerId).exec();
        jobIdTracker.put(jobId, containerId);
        return containerId;
    }

    @Override
    public void shutdownAll() {
        jobIdTracker.values().forEach(containerId -> {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        });
    }

    public static class BackendConfig extends SharedConfig {
        final String network;

        public BackendConfig(Properties properties) {
            super(properties);
            this.network = properties.getProperty("docker.network");
        }

    }
}
