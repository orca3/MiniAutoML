package org.orca3.miniAutoML.models;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class Commit implements RedisSerializable {
    private final String datasetId;
    private final String commitId;
    private final String uri;

    public Commit(long datasetId, long commitId, String uri) {
        this(Long.toString(datasetId), Long.toString(commitId), uri);
    }

    public Commit(String datasetId, long commitId, String uri) {
        this(datasetId, Long.toString(commitId), uri);
    }

    public Commit(String datasetId, String commitId, String uri) {
        this.datasetId = datasetId;
        this.commitId = commitId;
        this.uri = uri;
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

    @Override
    public Map<String, String> toRedisHash() {
        return ImmutableMap.<String, String>builder()
                .put("datasetId", datasetId)
                .put("commitId", commitId)
                .put("uri", uri)
                .build();
    }

    public static Commit fromRedisHash(Map<String, String> hash) {
        return new Commit(hash.get("datasetId"), hash.get("commitId"), hash.get("uri"));
    }

}
