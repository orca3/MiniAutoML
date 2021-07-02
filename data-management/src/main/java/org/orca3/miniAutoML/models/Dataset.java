package org.orca3.miniAutoML.models;

import org.orca3.miniAutoML.DatasetType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Dataset {
    private final String datasetId;
    private final String name;
    private final String description;
    private final DatasetType datasetType;
    private final String updatedAt;
    private final AtomicInteger commitIdSeed;
    public Map<String, Commit> commits;

    public Dataset(String datasetId, String name, String description, DatasetType datasetType, String updatedAt) {
        this.datasetId = datasetId;
        this.name = name;
        this.description = description;
        this.datasetType = datasetType;
        this.updatedAt = updatedAt;
        this.commits = new HashMap<>();
        this.commitIdSeed = new AtomicInteger();
    }

    public Dataset(long datasetId, String name, String description, DatasetType datasetType) {
        this(Long.toString(datasetId), name, description, datasetType, ISO_INSTANT.format(Instant.now()));
    }

    public int getNextCommitId() {
        return commitIdSeed.incrementAndGet();
    }

    public int getLastCommitId() {
        return commitIdSeed.get();
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

    public DatasetType getDatasetType() {
        return datasetType;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
