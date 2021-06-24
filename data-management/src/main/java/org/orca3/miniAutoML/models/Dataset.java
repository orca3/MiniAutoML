package org.orca3.miniAutoML.models;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Dataset {
    private final String datasetId;
    private final String name;
    private final String description;
    private final String datasetType;
    private final String updatedAt;
    public Map<String, Commit> commits;
    private final AtomicInteger commitIdSeed;

    public Dataset(String datasetId, String name, String description, String datasetType, String updatedAt) {
        this.datasetId = datasetId;
        this.name = name;
        this.description = description;
        this.datasetType = datasetType;
        this.updatedAt = updatedAt;
        this.commits = new HashMap<>();
        this.commitIdSeed = new AtomicInteger();
    }

    public int getNextCommitId() {
        return commitIdSeed.incrementAndGet();
    }

    public int getLastCommitId() {
        return commitIdSeed.get();
    }

    public Dataset(long datasetId, String name, String description, String datasetType) {
        this(Long.toString(datasetId), name, description, datasetType, ISO_INSTANT.format(Instant.now()));
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
