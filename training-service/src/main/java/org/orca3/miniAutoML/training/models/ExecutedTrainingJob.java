package org.orca3.miniAutoML.training.models;

import org.orca3.miniAutoML.training.TrainingJobMetadata;

import javax.annotation.Nullable;

public class ExecutedTrainingJob {
    private final Long launchedAt;
    private final Long finishedAt;
    private final boolean success;
    private final TrainingJobMetadata metadata;
    private final String message;

    public ExecutedTrainingJob(Long launchedAt, Long finishedAt, boolean success, TrainingJobMetadata metadata, String message) {
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

    @Nullable
    public Long getLaunchedAt() {
        return launchedAt;
    }

    @Nullable
    public Long getFinishedAt() {
        return finishedAt;
    }
}
