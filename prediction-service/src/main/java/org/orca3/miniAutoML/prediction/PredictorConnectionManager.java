package org.orca3.miniAutoML.prediction;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.HashMap;
import java.util.Map;

public class PredictorConnectionManager {
    private final Map<String, ManagedChannel> channels = new HashMap<>();
    private final Map<String, PredictorGrpc.PredictorBlockingStub> clients = new HashMap<>();

    public void put(String algorithm, String host, int port) {
        if (channels.containsKey(algorithm)) {
            channels.remove(algorithm).shutdown();
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        channels.put(algorithm, channel);
        clients.put(algorithm, PredictorGrpc.newBlockingStub(channel));
    }

    public boolean contains(String algorithm) {
        return clients.containsKey(algorithm);
    }

    public PredictorGrpc.PredictorBlockingStub getClient(String algorithm) {
        return clients.get(algorithm);
    }

    public void shutdown() {
        channels.values().forEach(ManagedChannel::shutdown);
    }
}
