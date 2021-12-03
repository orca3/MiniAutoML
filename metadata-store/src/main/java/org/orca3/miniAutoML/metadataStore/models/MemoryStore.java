package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.metadataStore.RunInfo;

import java.util.HashMap;
import java.util.Map;

public class MemoryStore {
    public final Map<String, RunInfo> runInfoMap;
    public final Map<String, ArtifactRepo> artifactRepos;
    public final Map<String, ArtifactInfo> runIdLookup;

    public MemoryStore() {
        this.runInfoMap = new HashMap<>();
        this.artifactRepos = new HashMap<>();
        this.runIdLookup = new HashMap<>();
    }

    public ArtifactRepo getRepo(String artifactName) {
        ArtifactRepo repo;
        if (artifactRepos.containsKey(artifactName)) {
            repo = artifactRepos.get(artifactName);
        } else {
            repo = new ArtifactRepo(artifactName);
            artifactRepos.put(artifactName, repo);
        }
        return repo;
    }

    public void put(String artifactName, String version, ArtifactInfo artifactInfo) {
        getRepo(artifactName).artifacts.put(version, artifactInfo);
        runIdLookup.put(artifactInfo.getRunId(), artifactInfo);
    }

    public void clear() {
        runInfoMap.clear();
        artifactRepos.clear();
    }
}
