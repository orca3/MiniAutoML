package org.orca3.miniAutoML.prediction;

import io.grpc.ManagedChannel;

public class CustomGrpcPredictorBackend implements PredictorBackend {
    private final PredictorGrpc.PredictorBlockingStub stub;

    public CustomGrpcPredictorBackend(ManagedChannel channel) {
        stub = PredictorGrpc.newBlockingStub(channel);
    }

    @Override
    public String predict(String runId, String document) {
        return stub.predictorPredict(PredictorPredictRequest.newBuilder()
                .setDocument(document).setRunId(runId).build()).getResponse();
    }
}
