package org.orca3.miniAutoML.dataManagement.models;

import com.opencsv.bean.CsvBindByPosition;

import java.util.Objects;

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

    public String getLabels() {
        return labels;
    }

    public String[] getSplicedLabels() {
        return labels.split(";");
    }

    public IntentText labels(String labels) {
        this.labels = labels;
        return this;
    }

    public IntentText labels(String[] labels) {
        this.labels = String.join(";", labels);
        return this;
    }

    @Override
    public String toString() {
        return "IntentText{" +
                "utterance='" + utterance + '\'' +
                ", labels='" + labels + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntentText that = (IntentText) o;
        return Objects.equals(utterance, that.utterance) && Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(utterance, labels);
    }
}
