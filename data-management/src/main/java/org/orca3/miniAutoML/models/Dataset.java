package org.orca3.miniAutoML.models;

import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Dataset implements RedisSerializable {
    private final String datasetId;
    private final String name;
    private final String description;
    private final String datasetType;
    private final String updatedAt;

    public Dataset(String datasetId, String name, String description, String datasetType, String updatedAt) {
        this.datasetId = datasetId;
        this.name = name;
        this.description = description;
        this.datasetType = datasetType;
        this.updatedAt = updatedAt;
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

    @Override
    public Map<String, String> toRedisHash() {
        return ImmutableMap.<String, String>builder()
                .put("datasetId", datasetId)
                .put("name", name)
                .put("description", description)
                .put("datasetType", datasetType)
                .put("updatedAt", updatedAt)
                .build();
    }

    public static Dataset fromRedisHash(Map<String, String> hash) {
        return new Dataset(hash.get("datasetId"), hash.get("name"), hash.get("description"), hash.get("datasetType"),
                hash.get("updatedAt"));
    }
}
