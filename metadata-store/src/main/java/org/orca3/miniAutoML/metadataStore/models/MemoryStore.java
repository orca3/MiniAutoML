package org.orca3.miniAutoML.metadataStore.models;

import java.util.HashMap;
import java.util.Map;

public class MemoryStore {
    public final Map<String, ExecutionInfo> executionInfoMap;

    public MemoryStore() {
        this.executionInfoMap = new HashMap<>();
    }

    public void clear() {
        executionInfoMap.clear();
    }
}
