package gr.uoa.di.madgik.statstool.mapping.entities;

public class Join {

    private final String first_table;
    private final String first_field;
    private final String second_table;
    private final String second_field;
    private final String array;

    public Join(String first_table, String first_field, String second_table, String second_field, String array) {
        this.first_table = first_table;
        this.first_field = first_field;
        this.second_table = second_table;
        this.second_field = second_field;
        this.array = array;
    }

    public String getFirst_table() {
        return first_table;
    }

    public String getFirst_field() {
        return first_field;
    }

    public String getSecond_table() {
        return second_table;
    }

    public String getSecond_field() {
        return second_field;
    }

    public String getArray() {
        return array;
    }
}
