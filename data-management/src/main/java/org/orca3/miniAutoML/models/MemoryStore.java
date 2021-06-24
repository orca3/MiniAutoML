package org.orca3.miniAutoML.models;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryStore {
    public final Map<String, Dataset> datasets;
    public final AtomicInteger datasetIdSeed;

    public MemoryStore() {
        this.datasets = new HashMap<>();
        this.datasetIdSeed = new AtomicInteger();
    }

    public void clear() {
        datasets.clear();
        datasetIdSeed.set(0);
    }
}
