package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.dataManagement.FileInfo;

public class ArtifactInfo {
    private final FileInfo fileInfo;
    private final String runId;

    public String getRunId() {
        return runId;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public ArtifactInfo(FileInfo fileInfo, String runId) {
        this.fileInfo = fileInfo;
        this.runId = runId;
    }
}
