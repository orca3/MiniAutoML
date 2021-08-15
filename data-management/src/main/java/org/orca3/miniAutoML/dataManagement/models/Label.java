package org.orca3.miniAutoML.dataManagement.models;

import com.opencsv.bean.CsvBindByPosition;

import java.util.Objects;

public class Label {
    @CsvBindByPosition(position = 0)
    private String label;

    @CsvBindByPosition(position = 1)
    private String index;

    public String getIndex() {
        return index;
    }

    public Label index(String index) {
        this.index = index;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Label label(String label) {
        this.label = label;
        return this;
    }

    @Override
    public String toString() {
        return "Label{" +
                "index='" + index + '\'' +
                ", label='" + label + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Label label1 = (Label) o;
        return Objects.equals(index, label1.index) && Objects.equals(label, label1.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, label);
    }
}
