package org.orca3.miniAutoML.training.models;

import com.google.common.base.Strings;
import org.orca3.miniAutoML.training.TrainingJobMetadata;

import javax.annotation.Nullable;

public class ExecutedTrainingJob {
    private final long launchedAt;
    private final Long finishedAt;
    private final boolean success;
    private final TrainingJobMetadata metadata;
    private final String message;

    ExecutedTrainingJob(long launchedAt, Long finishedAt, boolean success, TrainingJobMetadata metadata, String message) {
        this.launchedAt = launchedAt;
        this.finishedAt = finishedAt;
        this.success = success;
        this.metadata = metadata;
        this.message = Strings.nullToEmpty(message);
    }

    public ExecutedTrainingJob(Long launchedAt, TrainingJobMetadata metadata, String message) {
        this(launchedAt, null, false, metadata, message);
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

    public Long getLaunchedAt() {
        return launchedAt;
    }

    @Nullable
    public Long getFinishedAt() {
        return finishedAt;
    }

    public ExecutedTrainingJob finished(long finishedAt, boolean success, String message) {
        return new ExecutedTrainingJob(this.launchedAt, finishedAt, success, this.metadata, message);
    }
}
