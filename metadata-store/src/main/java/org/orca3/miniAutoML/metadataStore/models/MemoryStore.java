package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.metadataStore.RunInfo;

import java.util.HashMap;
import java.util.Map;

public class MemoryStore {
    public final Map<String, RunInfo> runInfoMap;
    public final Map<String, ArtifactRepo> artifactRepos;

    public MemoryStore() {
        this.runInfoMap = new HashMap<>();
        this.artifactRepos = new HashMap<>();
    }

    public void clear() {
        runInfoMap.clear();
        artifactRepos.clear();
    }
}
