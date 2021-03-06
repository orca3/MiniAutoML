package org.orca3.miniAutoML.dataManagement.models;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class IntentTextCollection {
    private List<IntentText> texts = Lists.newArrayList();
    /**
     * Key: label string
     * Value: label id
     */
    private Map<String, String> labels = Maps.newHashMap();

    public List<IntentText> getTexts() {
        return texts;
    }

    public IntentTextCollection texts(List<IntentText> texts) {
        this.texts = texts;
        return this;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public IntentTextCollection labels(Map<String, String> labels) {
        this.labels = labels;
        return this;
    }

    public Map<String, String> stats() {
        return ImmutableMap.<String, String>builder()
                .put("numLabels", Integer.toString(labels.size()))
                .put("numExamples", Integer.toString(texts.size()))
                .build();
    }
}
