package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Data object as shown in the Highcharts <a href="https://api.highcharts.com/highcharts/series.area.data">library</a>
 */
public class DataObject {

    @JsonProperty
    private String className;
    @JsonProperty
    private String color;
    @JsonProperty
    private Number colorIndex;
    @JsonProperty
    private String description;
    @JsonProperty
    private String drilldown;
    @JsonProperty
    private String id;
    @JsonProperty
    private Number labelrank;
    @JsonProperty
    private String name;
    @JsonProperty
    private boolean selected;
    @JsonProperty
    private Number x;
    @JsonProperty
    private Number y;

    public DataObject(Number x,Number y){
        this.className = null;
        this.color = null;
        this.colorIndex = null;
        this.description = null;
        this.drilldown = null;
        this.id = null;
        this.labelrank = null;
        this.name = null;
        this.selected = false;
        this.x = x;
        this.y = y;
    }
    public DataObject(String name,Number y){
        this.className = null;
        this.color = null;
        this.colorIndex = null;
        this.description = null;
        this.drilldown = null;
        this.id = null;
        this.labelrank = null;
        this.name = name;
        this.selected = false;
        this.x = null;
        this.y = y;
    }
    public DataObject(String className, String color,
                      Number colorIndex, String description,
                      String drilldown, String id,
                      Number labelrank, String name,
                      boolean selected, Number x, Number y) {
        this.className = className;
        this.color = color;
        this.colorIndex = colorIndex;
        this.description = description;
        this.drilldown = drilldown;
        this.id = id;
        this.labelrank = labelrank;
        this.name = name;
        this.selected = selected;
        this.x = x;
        this.y = y;
    }

    public DataObject() {}

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Number getColorIndex() {
        return colorIndex;
    }

    public void setColorIndex(Number colorIndex) {
        this.colorIndex = colorIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDrilldown() {
        return drilldown;
    }

    public void setDrilldown(String drilldown) {
        this.drilldown = drilldown;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Number getLabelrank() {
        return labelrank;
    }

    public void setLabelrank(Number labelrank) {
        this.labelrank = labelrank;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public Number getX() {
        return x;
    }

    public void setX(Number x) {
        this.x = x;
    }

    public Number getY() {
        return y;
    }

    public void setY(Number y) {
        this.y = y;
    }
}
