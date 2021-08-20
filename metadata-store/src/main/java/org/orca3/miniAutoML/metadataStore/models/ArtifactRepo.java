package org.orca3.miniAutoML.metadataStore.models;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ArtifactRepo {
    private final AtomicInteger seed;
    public final Map<String, ArtifactInfo> artifacts;
    private final String name;

    public ArtifactRepo(String name) {
        this.name = name;
        this.seed = new AtomicInteger();
        this.artifacts = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public int getSeed() {
        return seed.incrementAndGet();
    }
}
