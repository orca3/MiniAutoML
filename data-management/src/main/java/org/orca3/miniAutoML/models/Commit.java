package org.orca3.miniAutoML.models;

import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Commit implements RedisSerializable {
    private final String datasetId;
    private final String commitId;
    private final String uri;
    private final String commitType;
    private final String createdAt;

    public Commit(long datasetId, long commitId, String uri, String commitType) {
        this(Long.toString(datasetId), Long.toString(commitId), uri, commitType, ISO_INSTANT.format(Instant.now()));
    }

    public Commit(String datasetId, long commitId, String uri, String commitType) {
        this(datasetId, Long.toString(commitId), uri, commitType, ISO_INSTANT.format(Instant.now()));
    }

    public Commit(String datasetId, String commitId, String uri, String commitType, String createdAt) {
        this.datasetId = datasetId;
        this.commitId = commitId;
        this.uri = uri;
        this.commitType = commitType;
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

    public String getCommitType() {
        return commitType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public Map<String, String> toRedisHash() {
        return ImmutableMap.<String, String>builder()
                .put("datasetId", datasetId)
                .put("commitId", commitId)
                .put("uri", uri)
                .put("createdAt", createdAt)
                .put("commitType", commitType)
                .build();
    }

    public static Commit fromRedisHash(Map<String, String> hash) {
        return new Commit(hash.get("datasetId"), hash.get("commitId"), hash.get("uri"), hash.get("commitType"),
                hash.get("createdAt"));
    }

}
