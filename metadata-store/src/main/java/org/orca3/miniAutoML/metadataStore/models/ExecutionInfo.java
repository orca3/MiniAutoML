package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.metadataStore.TracingInformation;

import java.util.List;

public class ExecutionInfo {
    private final String startTime;
    private final String runId;
    private final String runName;
    private final TracingInformation tracing;
    private String endTime;
    private boolean success;
    private String message;

    public ExecutionInfo(String startTime, String runId, String runName, TracingInformation tracing) {
        this.startTime = startTime;
        this.runId = runId;
        this.runName = runName;
        this.tracing = tracing;
        this.endTime = "";
        this.success = false;
        this.message = "";
    }

    public ExecutionInfo endExecution(String endTime, boolean success, String message) {
        this.endTime = endTime;
        this.success = success;
        this.message = message;
        return this;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getRunId() {
        return runId;
    }

    public String getRunName() {
        return runName;
    }

    public TracingInformation getTracing() {
        return tracing;
    }

    public String getEndTime() {
        return endTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
