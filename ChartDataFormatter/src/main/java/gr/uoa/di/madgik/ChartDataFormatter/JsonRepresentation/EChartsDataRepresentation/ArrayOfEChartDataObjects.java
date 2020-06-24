package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.EChartsDataRepresentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;

import java.util.ArrayList;
@JsonDeserialize(using= JsonDeserializer.None.class)
public class ArrayOfEChartDataObjects implements AbsData {

    @JsonProperty
    private ArrayList<EChartsDataObject> data;

    public ArrayOfEChartDataObjects() {}

    public ArrayOfEChartDataObjects(ArrayList<EChartsDataObject> data) {
        this.data = data;
    }

    @Override
    public ArrayList<EChartsDataObject> getData() {
        return data;
    }

    public void setData(ArrayList<EChartsDataObject> data) {
        this.data = data;
    }
}