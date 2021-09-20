package org.orca3.miniAutoML.training.tracker;

import io.grpc.ManagedChannel;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import java.util.ArrayList;
import java.util.List;
import org.orca3.miniAutoML.dataManagement.VersionedSnapshot;
import org.orca3.miniAutoML.training.TrainingJobMetadata;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class KubectlTracker extends Tracker<KubectlTracker.BackendConfig> {
    final Map<Integer, String> jobIdTracker;
    // {0} is algorithm name; {1} is job id
    final static String MasterPodNamePattern = "%s-%d-master";
    final static String MasterServiceNamePattern = "%s-%d-master-service";
    // {0} is algorithm name; {1} is job id; {2} is worker rank
    final static String WorkerPodNamePattern = "%s-%d-%d-worker";

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
    // TODO: only allow one training happens at a time.
    public boolean hasCapacity() {
        return true;
    }

    private V1Pod launchMasterWorkerPod(int jobId, int worldSize, TrainingJobMetadata metadata, Map<String, String> envs) {
        CoreV1Api api = new CoreV1Api();

        String masterPodName = String.format(MasterPodNamePattern, metadata.getAlgorithm().toLowerCase(), jobId);
        String masterPodServiceName = String.format(MasterServiceNamePattern, metadata.getAlgorithm().toLowerCase(), jobId);

        // point master address to the master pod itself, since the master service has not setup at this moment.
        envs.put("MASTER_ADDR", masterPodName);
        envs.put("MASTER_PORT", "12356");
        envs.put("WORLD_SIZE", String.valueOf(worldSize));
        // RANK 0 is master
        envs.put("RANK", "0");

        // make sure master port is open
        List<V1ContainerPort> masterPorts= new ArrayList<V1ContainerPort>();
        V1ContainerPort port = new V1ContainerPort();
        port.setContainerPort(12356);
        masterPorts.add(port);

        V1PodSpec podSpec = new V1PodSpec().restartPolicy("Never");
        podSpec.addContainersItem(new V1Container()
            .name("traincode")
            .image(algorithmToImage(metadata.getAlgorithm()))
            // To work with localhost:3000 registry
            .imagePullPolicy("Never")
            .ports(masterPorts)
            .env(envs.entrySet().stream()
                .map(kvp -> new V1EnvVar().name(kvp.getKey()).value(kvp.getValue()))
                .collect(Collectors.toList()))
        );

        V1Pod podBody = new V1Pod();
        podBody.apiVersion("v1");
        podBody.kind("Pod");
        podBody.metadata(new V1ObjectMeta().name(masterPodName));
        podBody.getMetadata().labels(Map.of("name",masterPodName, "app", masterPodName));
        podBody.spec(podSpec);

        try {
            return api.createNamespacedPod(config.kubeNamespace, podBody, null, null, null);
        } catch (ApiException e) {
            logger.error(String.format("Cannot launch master pod for job %s", jobId), e);
            throw new RuntimeException(e);
        }
    }

    private V1Service launchMasterService(int jobId, TrainingJobMetadata metadata) {
        CoreV1Api api = new CoreV1Api();

        String masterPodName = String.format(MasterPodNamePattern, metadata.getAlgorithm().toLowerCase(), jobId);
        String masterPodServiceName = String.format(MasterServiceNamePattern, metadata.getAlgorithm().toLowerCase(), jobId);

        List<V1ServicePort> servicePorts = new ArrayList<>();
        V1ServicePort servicePort = new V1ServicePort();
        servicePort.setPort(12356);
        servicePort.setTargetPort(new IntOrString(12356));
        servicePorts.add(servicePort);

        V1Service serviceBody = new V1Service();
        serviceBody.apiVersion("v1");
        serviceBody.kind("Service");
        serviceBody.metadata(new V1ObjectMeta().name(masterPodServiceName));
        serviceBody.spec(new V1ServiceSpec()
            .selector(
                Map.of("app", masterPodName))
            .ports(servicePorts)
        );

        try {
            return api.createNamespacedService(config.kubeNamespace, serviceBody, null, null, null);
        } catch (ApiException e) {
            logger.error(String.format("Cannot launch master service for job %s", jobId), e);
            throw new RuntimeException(e);
        }
    }

    private List<V1Pod> launchWorkerPods(int jobId, int worldSize, TrainingJobMetadata metadata, Map<String, String> envs) {
        CoreV1Api api = new CoreV1Api();

        String masterPodServiceName = String.format(MasterServiceNamePattern, metadata.getAlgorithm().toLowerCase(), jobId);
        List<V1Pod> workerPods = new ArrayList<>();

        // create the rest distributed worker pods. Rank starts from 1.
        for (int rank = 1; rank < worldSize; rank++){
            envs.put("WORLD_SIZE", String.valueOf(worldSize));
            envs.put("RANK", String.valueOf(rank));
            envs.put("MASTER_ADDR", masterPodServiceName);
            envs.put("MASTER_PORT", "12356");

            V1PodSpec workerPodSpec = new V1PodSpec().restartPolicy("Never");
            workerPodSpec.addContainersItem(new V1Container()
                .name("traincode")
                .image(algorithmToImage(metadata.getAlgorithm()))
                // To work with localhost:3000 registry
                .imagePullPolicy("Never")
                .env(envs.entrySet().stream()
                    .map(kvp -> new V1EnvVar().name(kvp.getKey()).value(kvp.getValue()))
                    .collect(Collectors.toList()))
            );

            String workerPodName = String.format(WorkerPodNamePattern, metadata.getAlgorithm().toLowerCase(), jobId, rank);
            V1Pod workerPodBody = new V1Pod();
            workerPodBody.apiVersion("v1");
            workerPodBody.kind("Pod");
            workerPodBody.metadata(new V1ObjectMeta().name(workerPodName));
            workerPodBody.spec(workerPodSpec);

            try {
                workerPods.add(api.createNamespacedPod(config.kubeNamespace, workerPodBody, null, null, null));
            } catch (ApiException e) {
                logger.error(String.format("Cannot launch work pods for job %s with rank %d", jobId, rank), e);
                throw new RuntimeException(e);
            }
        }

        return workerPods;
    }

    protected String launchDistributedTrainingPods(int jobId, int worldSize, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        Map<String, String> envs = containerEnvVars(metadata, versionedSnapshot);

        // Launch distributed training worker group.

        // All parallel workers sync with each others through master worker, so it needs to be provisioned first.
        V1Pod masterWorkerPod = launchMasterWorkerPod(jobId, worldSize, metadata, envs);
        V1Service masterService = launchMasterService(jobId, metadata);
        List<V1Pod> workerPods = launchWorkerPods(jobId, worldSize, metadata, envs);

        // TODO: to track status, we only need to look at master worker pod, since all workers are in the same pace
        // with master worker, and master worker pod is the one who saves model and upload to metadata store.
        // It's better for us to clean up all the pods and service after training is done, but it will wipe out
        // all the history and logs. Since this code is written for demo purpose, we choose to not delete them to keep the
        // history for troubleshooting.
        return null;
    }

    @Override
    protected String launch(int jobId, TrainingJobMetadata metadata, VersionedSnapshot versionedSnapshot) {
        int worldSize = 1;
        if (metadata.getParametersMap().containsKey("PARALLEL_INSTANCES")) {
            int cnt = Integer.parseInt(metadata.getParametersMap().get("PARALLEL_INSTANCES"));
            if (cnt > worldSize) {
                worldSize = cnt;
            }
        }

        if (worldSize > 1) {
            return launchDistributedTrainingPods(jobId, worldSize, metadata, versionedSnapshot);
        }

        BatchV1Api api = new BatchV1Api();
        Map<String, String> envs = containerEnvVars(metadata, versionedSnapshot);
        String jobName = String.format("%d-%d-%s", jobId, System.currentTimeMillis(), metadata.getName());
        V1PodSpec podSpec = new V1PodSpec().restartPolicy("Never");
        podSpec.addContainersItem(new V1Container()
                .name("workload")
                .image(algorithmToImage(metadata.getAlgorithm()))
                // To work with localhost:3000 registry
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

    protected String algorithmToImage(String algorithm) {
        return String.format("localhost:3000/orca3/%s", algorithm);
    }
}
