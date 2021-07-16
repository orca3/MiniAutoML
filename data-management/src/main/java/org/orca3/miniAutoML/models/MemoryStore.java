package org.orca3.miniAutoML.models;

import org.orca3.miniAutoML.VersionedSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryStore {
    public final Map<String, Dataset> datasets;
    public final AtomicInteger datasetIdSeed;
    public final Map<String, VersionedSnapshot> versionHashRegistry;

    public MemoryStore() {
        this.datasets = new HashMap<>();
        this.datasetIdSeed = new AtomicInteger();
        this.versionHashRegistry = new HashMap<>();
    }

    public void clear() {
        datasets.clear();
        datasetIdSeed.set(0);
    }

    public static String calculateVersionHashKey(String datasetId, String versionHash) {
        return String.format("dataset%s:%s", datasetId, versionHash);
    }
}
