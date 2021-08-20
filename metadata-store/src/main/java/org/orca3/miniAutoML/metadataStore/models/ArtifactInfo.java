package org.orca3.miniAutoML.metadataStore.models;

import org.orca3.miniAutoML.dataManagement.FileInfo;

public class ArtifactInfo {
    private final FileInfo fileInfo;

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public ArtifactInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }
}
