package org.orca3.miniAutoML.models;

import com.opencsv.bean.CsvBindByPosition;

import java.util.List;

public class IntentText {
    @CsvBindByPosition(position = 0)
    private String utterance;

    @CsvBindByPosition(position = 1)
    private String labels;

    public String getUtterance() {
        return utterance;
    }

    public IntentText utterance(String utterance) {
        this.utterance = utterance;
        return this;
    }

    public String[] getLabels() {
        return labels.split(";");
    }

    public IntentText labels(String labels) {
        this.labels = labels;
        return this;
    }
}
