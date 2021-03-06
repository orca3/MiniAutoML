package org.orca3.miniAutoML.training;

import com.google.common.base.Strings;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;
import org.orca3.miniAutoML.training.tracker.DockerTracker;
import org.orca3.miniAutoML.training.tracker.KubectlTracker;
import org.orca3.miniAutoML.training.tracker.Tracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TrainingService extends TrainingServiceGrpc.TrainingServiceImplBase {
    private final MemoryStore store;
    private final Config config;
    private static final Logger logger = LoggerFactory.getLogger(TrainingService.class);

    public TrainingService(MemoryStore store, Config config) {
        this.config = config;
        this.store = store;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("Hello, Training Service!");
        Properties props = ServiceBase.getConfigProperties();
        Config config = new Config(props);

        MemoryStore store = new MemoryStore();
        ManagedChannel dmChannel = ManagedChannelBuilder.forAddress(config.dmHost, Integer.parseInt(config.dmPort))
                .usePlaintext().build();
        Tracker<?> tracker;
        if (config.backend.equals("docker")) {
            logger.info("Using docker backend.");
            tracker = new DockerTracker(store, props, dmChannel);
        } else if (config.backend.equals("kubectl")) {
            logger.info("Using kubernetes backend.");
            tracker = new KubectlTracker(store, props, dmChannel);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported backend %s", config.backend));
        }
        TrainingService trainingService = new TrainingService(store, config);
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        final ScheduledFuture<?> launchingTask =
                scheduler.scheduleAtFixedRate(tracker::launchAll, 5, 5, SECONDS);
        final ScheduledFuture<?> refreshingTask =
                scheduler.scheduleAtFixedRate(tracker::updateContainerStatus, 7, 5, SECONDS);
        ServiceBase.startService(Integer.parseInt(config.serverPort), trainingService, () -> {
            dmChannel.shutdownNow();
            tracker.shutdownAll();
            launchingTask.cancel(true);
            refreshingTask.cancel(true);
            scheduler.shutdown();

        });
    }

    static class Config {
        final String serverPort;
        final String dmHost;
        final String dmPort;
        final String backend;

        public Config(Properties properties) {
            this.serverPort = properties.getProperty("ts.server.port");
            this.dmPort = properties.getProperty("dm.server.port");
            this.dmHost = properties.getProperty("dm.server.host");
            this.backend = properties.getProperty("ts.backend");
        }
    }

    @Override
    public void train(TrainRequest request, StreamObserver<TrainResponse> responseObserver) {
        if (Strings.isNullOrEmpty(request.getMetadata().getOutputModelName())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("\"outputModelName\" is required.")
                    .asException());
            return;
        }
        if (Strings.isNullOrEmpty(request.getMetadata().getDatasetId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("\"datasetId\" is required.")
                    .asException());
            return;
        }
        if (Strings.isNullOrEmpty(request.getMetadata().getTrainDataVersionHash())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("\"trainDataVersionHash\" is required.")
                    .asException());
            return;
        }
        if (Strings.isNullOrEmpty(request.getMetadata().getAlgorithm())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("\"algorithm\" is required.")
                    .asException());
            return;
        }
        int jobId = store.offer(request);
        responseObserver.onNext(TrainResponse.newBuilder().setJobId(jobId).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTrainingStatus(GetTrainingStatusRequest request, StreamObserver<GetTrainingStatusResponse> responseObserver) {
        int jobId = request.getJobId();
        final ExecutedTrainingJob job;
        final TrainingStatus status;
        if (store.finalizedJobs.containsKey(jobId)) {
            job = store.finalizedJobs.get(jobId);
            status = job.isSuccess() ? TrainingStatus.succeed : TrainingStatus.failure;
        } else if (store.launchingList.containsKey(jobId)) {
            job = store.launchingList.get(jobId);
            status = TrainingStatus.launch;
        } else if (store.runningList.containsKey(jobId)) {
            job = store.runningList.get(jobId);
            status = TrainingStatus.running;
        } else {
            TrainingJobMetadata metadata = store.jobQueue.get(jobId);
            if (metadata != null) {
                int position = store.getQueuePosition(jobId);
                responseObserver.onNext(GetTrainingStatusResponse.newBuilder()
                        .setJobId(jobId)
                        .setStatus(TrainingStatus.queuing)
                        .setMetadata(metadata)
                        .setMessage(String.format("Queueing, there are %s training jobs waiting before this.", position))
                        .setPositionInQueue(position)
                        .build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription(String.format("Job %s doesn't exist", jobId))
                        .asException());
            }
            return;
        }
        responseObserver.onNext(GetTrainingStatusResponse.newBuilder()
                .setJobId(jobId)
                .setStatus(status)
                .setMessage(job.getMessage())
                .setMetadata(job.getMetadata())
                .build());
        responseObserver.onCompleted();
    }
}
