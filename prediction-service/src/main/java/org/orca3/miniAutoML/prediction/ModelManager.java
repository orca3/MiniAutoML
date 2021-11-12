package org.orca3.miniAutoML.prediction;

import org.orca3.miniAutoML.dataManagement.FileInfo;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ModelManager {
    private final String modelCachePath;

    public ModelManager(String modelCachePath) {
        this.modelCachePath = modelCachePath;
    }

    public boolean contains(String runId) {
        return Files.exists(Paths.get(this.modelCachePath, runId));
    }

    public void set(String runId, FileInfo artifactLocation) {
        throw new java.lang.UnsupportedOperationException();
    }
}
