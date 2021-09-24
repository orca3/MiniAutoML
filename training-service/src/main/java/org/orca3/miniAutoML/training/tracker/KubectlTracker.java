package org.orca3.miniAutoML.training.tracker;

import io.grpc.ManagedChannel;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class KubectlTracker extends Tracker<KubectlTracker.BackendConfig> {
    final Map<Integer, List<String>> jobIdTracker;
    final List<String> serviceTracker;

    public KubectlTracker(MemoryStore store, Properties props, ManagedChannel dmChannel) throws IOException {
        super(store, dmChannel, LoggerFactory.getLogger(KubectlTracker.class), new BackendConfig(props));
        ApiClient client = ClientBuilder.kubeconfig(
                KubeConfig.loadKubeConfig(new FileReader(config.kubeConfigFilePath))).build();
        Configuration.setDefaultApiClient(client);
        jobIdTracker = new HashMap<>();
        serviceTracker = new LinkedList<>();
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

    protected List<String> launchTrainingPods(int jobId, int worldSize, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        Map<String, String> envs = containerEnvVars(jobId, metadata, versionedSnapshot);
        CoreV1Api api = new CoreV1Api();
        long now = System.currentTimeMillis();
        int masterPort = 12356;
        List<String> podNames = new LinkedList<>();
        String masterPodName = String.format("job-%d-%d-%s-master", jobId, now, metadata.getName());
        String masterServiceName = String.format("job-%d-%d-%s-service", jobId, now, metadata.getName());
        String masterPodDnsName =  String.format("%s.%s.svc.cluster.local", masterServiceName, config.kubeNamespace);

        if (worldSize > 1) {
            V1Service serviceBody = new V1Service().apiVersion("v1").kind("Service")
                    .metadata(new V1ObjectMeta().name(masterServiceName))
                    .spec(new V1ServiceSpec()
                    .selector(Map.of("app", masterPodName))
                            .addPortsItem(new V1ServicePort().port(masterPort).targetPort(new IntOrString(masterPort)))
            );
            try {
                api.createNamespacedService(config.kubeNamespace, serviceBody, null, null, null);
                serviceTracker.add(masterServiceName);
                logger.info(String.format("Launched master service %s", masterServiceName));
            } catch (ApiException e) {
                logger.error(String.format("Cannot launch master service for job %s", jobId), e);
                throw new RuntimeException(e);
            }
        }

        for (int rank = 0; rank < worldSize; rank++) {
            envs.put("WORLD_SIZE", Integer.toString(worldSize));
            // RANK 0 is master
            envs.put("RANK", Integer.toString(rank));
            envs.put("MASTER_ADDR", masterPodDnsName);
            envs.put("MASTER_PORT", Integer.toString(masterPort));

            V1PodSpec podSpec = new V1PodSpec().restartPolicy("Never").addContainersItem(
                    new V1Container()
                            .name("traincode")
                            .image(algorithmToImage(metadata.getAlgorithm()))
                            .imagePullPolicy("Never")
                            .ports(List.of(new V1ContainerPort().containerPort(masterPort)))
                            .env(envVarsToList(envs))
            );

            String workerPodName = rank == 0 ? masterPodName : String.format("job-%d-%d-%s-worker-%d", jobId, now, metadata.getName(), rank);
            V1Pod workerPodBody = new V1Pod();
            workerPodBody.apiVersion("v1");
            workerPodBody.kind("Pod");
            workerPodBody.metadata(new V1ObjectMeta().name(workerPodName).labels(Map.of("app", workerPodName)));
            workerPodBody.spec(podSpec);

            try {
                api.createNamespacedPod(config.kubeNamespace, workerPodBody, null, null, null);
                podNames.add(workerPodName);
                logger.info(String.format("Launched worker pod %s", workerPodName));
            } catch (ApiException e) {
                logger.error(String.format("Cannot launch worker pods for job %s with rank %d", jobId, rank), e);
                throw new RuntimeException(e);
            }
        }
        return podNames;
    }

    @Override
    protected String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        int worldSize = 1;
        if (metadata.getParametersMap().containsKey("PARALLEL_INSTANCES")) {
            worldSize = Integer.parseInt(metadata.getParametersMap().get("PARALLEL_INSTANCES"));
        }

        List<String> podNames = launchTrainingPods(jobId, worldSize, metadata, versionedSnapshot);
        jobIdTracker.put(jobId, podNames);
        return podNames.get(0);
    }

    @Override
    public void updateContainerStatus() {
        CoreV1Api api = new CoreV1Api();
        Set<Integer> launchingJobs = store.launchingList.keySet();
        Set<Integer> runningJobs = store.runningList.keySet();
        logger.info(String.format("Scanning %d jobs", launchingJobs.size() + runningJobs.size()));
        for (Integer jobId : launchingJobs) {
            List<String> podName = jobIdTracker.get(jobId);
            V1PodStatus state;
            try {
                state = api.readNamespacedPod(podName.get(0), config.kubeNamespace, null, null, null).getStatus();
                Objects.requireNonNull(state);
            } catch (ApiException e) {
                logger.warn(String.format("Failed to refresh job %s status", podName), e);
                continue;
            }
            switch (Objects.requireNonNull(state.getPhase())) {
                case "Running":
                    // move to running
                    ExecutedTrainingJob running = store.launchingList.remove(jobId);
                    store.runningList.put(jobId, running);
                    break;
                case "Succeeded":
                    ExecutedTrainingJob stopped = store.launchingList.remove(jobId);
                    store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), true, state.getMessage()));
                    break;
                case "Failed":
                    ExecutedTrainingJob failed = store.launchingList.remove(jobId);
                    store.finalizedJobs.put(jobId, failed.finished(System.currentTimeMillis(), false, state.getMessage()));
                    break;
                default:
                    // Pending / Unknown, do nothing
            }
        }
        for (Integer jobId : runningJobs) {
            List<String> podName = jobIdTracker.get(jobId);
            V1PodStatus state;
            try {
                state = api.readNamespacedPod(podName.get(0), config.kubeNamespace, null, null, null).getStatus();
                Objects.requireNonNull(state);
            } catch (ApiException e) {
                logger.warn(String.format("Failed to refresh job %s status", podName), e);
                continue;
            }
            switch (Objects.requireNonNull(state.getPhase())) {
                case "Succeeded":
                    ExecutedTrainingJob stopped = store.runningList.remove(jobId);
                    store.finalizedJobs.put(jobId, stopped.finished(System.currentTimeMillis(), true, state.getMessage()));
                    break;
                case "Failed":
                    ExecutedTrainingJob failed = store.runningList.remove(jobId);
                    store.finalizedJobs.put(jobId, failed.finished(System.currentTimeMillis(), false, state.getMessage()));
                    break;
                default:
                    // Running / Pending / Unknown, do nothing
            }
        }
    }

    @Override
    public void shutdownAll() {
        CoreV1Api api = new CoreV1Api();
        jobIdTracker.values().forEach(podNames -> podNames.forEach(podName -> {
            try {
                api.deleteNamespacedPod(podName, config.kubeNamespace, null, null, 0, null, "Background", null);
                logger.info(String.format("Deleted kubectl pod %s in %s", podName, config.kubeNamespace));
            } catch (ApiException e) {
                logger.error(String.format("Failed to delete kubectl pod %s in %s", podName, config.kubeNamespace), e);
            }
        }));
        serviceTracker.forEach(svcName -> {
            try {
                api.deleteNamespacedService(svcName, config.kubeNamespace, null, null, 0, null, "Background", null);
                logger.info(String.format("Deleted kubectl svc %s in %s", svcName, config.kubeNamespace));
            } catch (ApiException e) {
                logger.error(String.format("Failed to delete kubectl svc %s in %s", svcName, config.kubeNamespace), e);
            }
        });
    }

    protected String algorithmToImage(String algorithm) {
        return String.format("localhost:3000/orca3/%s", algorithm);
    }

    protected List<V1EnvVar> envVarsToList(Map<String, String> envs) {
        return envs.entrySet().stream()
                .map(kvp -> new V1EnvVar().name(kvp.getKey()).value(kvp.getValue()))
                .collect(Collectors.toList());
    }
}
