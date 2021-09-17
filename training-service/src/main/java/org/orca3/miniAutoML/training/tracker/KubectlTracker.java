package org.orca3.miniAutoML.training.tracker;

import com.github.dockerjava.api.command.InspectContainerResponse;
import io.grpc.ManagedChannel;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class KubectlTracker extends Tracker<KubectlTracker.BackendConfig> {
    final Map<Integer, String> jobIdTracker;

    public KubectlTracker(MemoryStore store, Properties props, ManagedChannel dmChannel) throws IOException {
        super(store, dmChannel, LoggerFactory.getLogger(KubectlTracker.class), new BackendConfig(props));
        ApiClient client = ClientBuilder.kubeconfig(
                KubeConfig.loadKubeConfig(new FileReader(config.kubeConfigFilePath))).build();
        Configuration.setDefaultApiClient(client);
        jobIdTracker = new HashMap<>();
    }

    public static class BackendConfig extends SharedConfig {
        final String kubeConfigFilePath;
        final String kubeNamespace;

        public BackendConfig(Properties properties) {
            super(properties);
            this.kubeConfigFilePath = properties.getProperty("kubectl.configFile");
            this.kubeNamespace = properties.getProperty("kubectl.namespace");
        }

    }

    @Override
    public boolean hasCapacity() {
        return true;
    }

    @Override
    protected String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        BatchV1Api api = new BatchV1Api();
        Map<String, String> envs = containerEnvVars(metadata, versionedSnapshot);
        String jobName = String.format("%d-%d-%s", jobId, System.currentTimeMillis(), metadata.getName());
        V1PodSpec podSpec = new V1PodSpec().restartPolicy("Never");
        podSpec.addContainersItem(new V1Container()
                .name("workload")
                .image(algorithmToImage(metadata.getAlgorithm()))
                .imagePullPolicy("Never")
                .env(envs.entrySet().stream()
                        .map(kvp -> new V1EnvVar().name(kvp.getKey()).value(kvp.getValue()))
                        .collect(Collectors.toList()))
        );
        V1Job kubejob = new V1Job()
                .apiVersion("batch/v1")
                .kind("Job")
                .metadata(new V1ObjectMeta().name(jobName))
                .spec(new V1JobSpec().template(
                        new V1PodTemplateSpec().spec(podSpec)).backoffLimit(0));
        try {
            api.createNamespacedJob(config.kubeNamespace, kubejob, null, null, null);
        } catch (ApiException e) {
            logger.error(String.format("Cannot launch job %s", jobId), e);
            throw new RuntimeException(e);
        }
        jobIdTracker.put(jobId, jobName);
        return jobName;
    }

    @Override
    public void updateContainerStatus() {
        BatchV1Api api = new BatchV1Api();
        Set<Integer> launchingJobs = store.launchingList.keySet();
        Set<Integer> runningJobs = store.runningList.keySet();
        logger.info(String.format("Scanning %d jobs", launchingJobs.size() + runningJobs.size()));
        for (Integer jobId : launchingJobs) {
            String jobName = jobIdTracker.get(jobId);
            V1JobStatus state = null;
            try {
                state = api.readNamespacedJob(jobName, config.kubeNamespace, null, null, null).getStatus();
                Objects.requireNonNull(state);
            } catch (ApiException e) {
                logger.warn(String.format("Failed to refresh job %s status", jobName), e);
                continue;
            }
            if (state.getActive() != null) {
                // move to running
                ExecutedTrainingJob running = store.launchingList.remove(jobId);
                store.runningList.put(jobId, running);
            } else {
                ExecutedTrainingJob stopped = store.launchingList.remove(jobId);
                store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), state.getSucceeded() != null,""));
            }
        }
        for (Integer jobId : runningJobs) {
            String jobName = jobIdTracker.get(jobId);
            V1JobStatus state = null;
            try {
                state = api.readNamespacedJob(jobName, config.kubeNamespace, null, null, null).getStatus();
                Objects.requireNonNull(state);
            } catch (ApiException e) {
                logger.warn(String.format("Failed to refresh job %s status", jobName), e);
                continue;
            }
            if (state.getActive() == null) {
                ExecutedTrainingJob stopped = store.runningList.remove(jobId);
                store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), state.getSucceeded() != null,""));
            }
        }
    }

    @Override
    public void shutdownAll() {
        BatchV1Api api = new BatchV1Api();
        jobIdTracker.values().forEach(jobName -> {
            try {
                api.deleteNamespacedJob(jobName, config.kubeNamespace, null, null, 0, null, "Background", null);
            } catch (ApiException e) {
                logger.error(String.format("Failed to delete kubectl job %s in %s", jobName, config.kubeNamespace), e);
            }
        });

    }
}
