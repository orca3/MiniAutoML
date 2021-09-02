package org.orca3.miniAutoML.training;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.orca3.miniAutoML.ServiceBase;
import org.orca3.miniAutoML.metadataStore.tracker.DockerTracker;
import org.orca3.miniAutoML.training.models.ExecutedTrainingJob;
import org.orca3.miniAutoML.training.models.MemoryStore;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TrainingService extends TrainingServiceGrpc.TrainingServiceImplBase {
    private final MemoryStore store;
    private final Config config;

    public TrainingService(MemoryStore store, Config config) {
        this.config = config;
        this.store = store;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = new Properties();
        props.load(TrainingService.class.getClassLoader().getResourceAsStream("config.properties"));
        Config config = new Config(props);

        MemoryStore store = new MemoryStore();
        DockerTracker tracker = new DockerTracker(store);
        TrainingService trainingService = new TrainingService(store, config);
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final ScheduledFuture<?> launchingThread =
                scheduler.scheduleAtFixedRate(tracker::launchAll, 10, 10, SECONDS);
        ServiceBase.startService(Integer.parseInt(config.serverPort), trainingService, () -> {
            tracker.shutdownAll();
            launchingThread.cancel(true);
        });
    }

    static class Config {
        final String serverPort;

        public Config(Properties properties) {
            this.serverPort = properties.getProperty("server.port");
        }
    }

    @Override
    public void train(TrainRequest request, StreamObserver<TrainResponse> responseObserver) {
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
            int position = store.getQueuePosition(jobId);
            if (position != -1) {
                TrainingJobMetadata metadata = store.jobQueue.get(jobId);
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
                .setPositionInQueue(-1)
                .build());
        responseObserver.onCompleted();
    }
}
