package org.orca3.miniAutoML.prediction;

import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;

public interface PredictorBackend {
    void downloadModel(String runId, GetArtifactResponse artifactResponse);

    String predict(GetArtifactResponse artifact, String document);

    void registerModel(GetArtifactResponse artifact);
}
