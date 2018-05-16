package gr.uoa.di.madgik.statstool.domain;

import java.util.List;

public class FieldValues {

    private int count;
    private List<String> values;

    public FieldValues(int count, List<String> values) {
        this.count = count;
        this.values = values;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
