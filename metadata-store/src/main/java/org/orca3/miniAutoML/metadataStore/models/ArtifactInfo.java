package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.dataManagement.FileInfo;

public class ArtifactInfo {
    private final FileInfo fileInfo;
    private final String runId;
    private final String artifactName;
    private final String version;
    private final String algorithm;

    public String getRunId() {
        return runId;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getVersion() {
        return version;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public ArtifactInfo(FileInfo fileInfo, String runId, String artifactName, String version, String algorithm) {
        this.fileInfo = fileInfo;
        this.runId = runId;
        this.artifactName = artifactName;
        this.version = version;
        this.algorithm = algorithm;
    }
}
