package org.orca3.miniAutoML.prediction;

public interface PredictorBackend {
    // TODO: Replace with Torch server protocol (bytes & map)
    String predict(String runId, String document);
}
