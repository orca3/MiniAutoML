package org.orca3.miniAutoML.training.models;

import org.orca3.miniAutoML.training.TrainingJobMetadata;

public class ExecutedTrainingJob {
    private final String launchedAt;
    private final String finishedAt;
    private final boolean success;
    private final TrainingJobMetadata metadata;
    private final String message;

    public ExecutedTrainingJob(String launchedAt, String finishedAt, boolean success, TrainingJobMetadata metadata, String message) {
        this.launchedAt = launchedAt;
        this.finishedAt = finishedAt;
        this.success = success;
        this.metadata = metadata;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public TrainingJobMetadata getMetadata() {
        return metadata;
    }

    public String getMessage() {
        return message;
    }

    public String getLaunchedAt() {
        return launchedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }
}
