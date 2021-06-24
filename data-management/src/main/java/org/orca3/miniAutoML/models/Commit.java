package org.orca3.miniAutoML.models;

import org.orca3.miniAutoML.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Commit {
    private final String datasetId;
    private final String commitId;
    private final String uri;
    private final String createdAt;
    private final Map<String, String> tags;

    public Commit(int datasetId, int commitId, String uri, List<Tag> tags) {
        this(Integer.toString(datasetId), Integer.toString(commitId), uri, ISO_INSTANT.format(Instant.now()), tags);
    }

    public Commit(String datasetId, int commitId, String uri, List<Tag> tags) {
        this(datasetId, Integer.toString(commitId), uri, ISO_INSTANT.format(Instant.now()), tags);
    }

    public Commit(String datasetId, String commitId, String uri, String createdAt, List<Tag> tags) {
        this.datasetId = datasetId;
        this.commitId = commitId;
        this.uri = uri;
        this.createdAt = createdAt;
        this.tags = tags.stream().collect(Collectors.toMap(Tag::getTagKey, Tag::getTagValue));
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

    public Map<String, String> getTags() {
        return tags;
    }
}
