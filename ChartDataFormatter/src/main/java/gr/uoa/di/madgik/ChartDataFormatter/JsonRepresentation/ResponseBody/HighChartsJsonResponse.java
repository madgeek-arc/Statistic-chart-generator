package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.*;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class HighChartsJsonResponse extends JsonResponse {

    @JsonProperty(value = "series")
    private List<AbsData> dataSeries;

    private List<String> xAxis_categories;

    // dataSeriesNames list must be of the same size as dataSeries list
    private List<String> dataSeriesNames;

    // dataSeriesType list must be of the same size as dataSeries list
    private List<String> dataSeriesTypes;

    private List<AbsData> drilldown;

    private final Logger log = Logger.getLogger(this.getClass());

    public HighChartsJsonResponse() {}

    public HighChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = null;
        this.dataSeriesTypes = null;
        this.drilldown = null;
    }

    public HighChartsJsonResponse(List<AbsData> dataSeries, List<String> xAxis_categories, List<String> dataSeriesNames, List<String> dataSeriesTypes) {
        this.dataSeries = dataSeries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = dataSeriesNames;
        this.dataSeriesTypes = dataSeriesTypes;
        this.drilldown = null;
    }

    @Override
    public JsonResponse sort(String field) {
        switch (field) {
            case  "xaxis":
                return this;
            case "yaxis":
                if (xAxis_categories.size() == 0)
                    return this;
                if (dataSeries.size() == 0)
                    return this;
                List<Tuple> tuples = dataToTuples();

                tuples.sort((o1, o2) -> {
                    Number n1 = null;
                    Number n2 = null;

                    if (o1.getDataseries().get(0).getData() instanceof ArrayOfArrays) {
                        n1= ((ArrayOfArrays) o1.getDataseries().get(0).getData()).getData().get(0)[0];
                        n2= ((ArrayOfArrays) o2.getDataseries().get(0).getData()).getData().get(0)[0];
                    } else if (o1.getDataseries().get(0).getData() instanceof ArrayOfValues) {
                        n1 = ((ArrayOfValues) o1.getDataseries().get(0).getData()).getData().get(0);
                        n2 = ((ArrayOfValues) o2.getDataseries().get(0).getData()).getData().get(0);
                    } else if (o1.getDataseries().get(0).getData() instanceof ArrayOfDataObjects) {
                        n1 = ((ArrayOfDataObjects) o1.getDataseries().get(0).getData()).getData().get(0).getY();
                        n2 = ((ArrayOfDataObjects) o2.getDataseries().get(0).getData()).getData().get(0).getY();
                    } else
                        throw new IllegalArgumentException("WTF is the type of data to sort?!? " + o1.getDataseries().get(0).getData().getClass());

                    if (n1 == null)
                        return 1;
                    else if (n2 == null)
                        return -1;
                    else
                        return new BigDecimal(n1.toString()).compareTo(new BigDecimal(n2.toString()));
                });

                return TuplesToJsonResponse(tuples);
            default:
                throw new IllegalArgumentException("Field should be either 'xaxis' or 'yxaxis'");
        }
    }

    private HighChartsJsonResponse TuplesToJsonResponse(List<Tuple> tuples) {
        HighChartsJsonResponse response = new HighChartsJsonResponse();

        return this;
    }

    private List<Tuple> dataToTuples() {
        List<Tuple> tuples = new ArrayList<>();

        return tuples;
    }

    @Override
    public String toString() {
        return "HighChartsJsonResponse{" +
                "dataSeries=" + dataSeries +
                ", xAxis_categories=" + xAxis_categories +
                ", dataSeriesNames=" + dataSeriesNames +
                ", dataSeriesTypes=" + dataSeriesTypes +
                ", drilldown=" + drilldown +
                '}';
    }

    public List<AbsData> getDataSeries() {
        return dataSeries;
    }

    public void setDataSeries(List<AbsData> dataSeries) {
        this.dataSeries = dataSeries;
    }

    public List<String> getxAxis_categories() {
        return xAxis_categories;
    }

    public void setxAxis_categories(List<String> xAxis_categories) {
        this.xAxis_categories = xAxis_categories;
    }

    public List<String> getDataSeriesNames() { return dataSeriesNames; }

    public void setDataSeriesNames(List<String> dataSeriesNames) { this.dataSeriesNames = dataSeriesNames; }

    public List<String> getDataSeriesTypes() { return dataSeriesTypes; }

    public void setDataSeriesTypes(List<String> dataSeriesType) { this.dataSeriesTypes = dataSeriesType; }

    public List<AbsData> getDrilldown() { return drilldown; }

    public void setDrilldown(List<AbsData> drilldown) { this.drilldown = drilldown; }
}

class Tuple {
    private List<AbsData> dataseries;
    private String xAxis_categories;
    private String dataSeriesNames;
    private String dataSeriesTypes;
    private AbsData drilldown;

    public Tuple(List<AbsData> dataseries, String xAxis_categories, String dataSeriesNames, String dataSeriesTypes, AbsData drilldown) {
        this.dataseries = dataseries;
        this.xAxis_categories = xAxis_categories;
        this.dataSeriesNames = dataSeriesNames;
        this.dataSeriesTypes = dataSeriesTypes;
        this.drilldown = drilldown;
    }

    public List<AbsData> getDataseries() {
        return dataseries;
    }

    public void setDataseries(List<AbsData> dataseries) {
        this.dataseries = dataseries;
    }

    public String getxAxis_categories() {
        return xAxis_categories;
    }

    public void setxAxis_categories(String xAxis_categories) {
        this.xAxis_categories = xAxis_categories;
    }

    public String getDataSeriesNames() {
        return dataSeriesNames;
    }

    public void setDataSeriesNames(String dataSeriesNames) {
        this.dataSeriesNames = dataSeriesNames;
    }

    public String getDataSeriesTypes() {
        return dataSeriesTypes;
    }

    public void setDataSeriesTypes(String dataSeriesTypes) {
        this.dataSeriesTypes = dataSeriesTypes;
    }

    public AbsData getDrilldown() {
        return drilldown;
    }

    public void setDrilldown(AbsData drilldown) {
        this.drilldown = drilldown;
    }
}