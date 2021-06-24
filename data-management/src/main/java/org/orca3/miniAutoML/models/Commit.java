package org.orca3.miniAutoML.models;

import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Commit {
    private final String datasetId;
    private final String commitId;
    private final String uri;
    private final String createdAt;

    public Commit(int datasetId, int commitId, String uri) {
        this(Integer.toString(datasetId), Integer.toString(commitId), uri, ISO_INSTANT.format(Instant.now()));
    }

    public Commit(String datasetId, int commitId, String uri) {
        this(datasetId, Integer.toString(commitId), uri, ISO_INSTANT.format(Instant.now()));
    }

    public Commit(String datasetId, String commitId, String uri, String createdAt) {
        this.datasetId = datasetId;
        this.commitId = commitId;
        this.uri = uri;
        this.createdAt = createdAt;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getUri() {
        return uri;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
