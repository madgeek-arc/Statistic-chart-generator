package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;

public class SupportedChart extends SupportedDiagram {
    private SupportedChartTypes type;

    public void setType(SupportedChartTypes type) {
        this.type = type;
    }

    public SupportedChartTypes getType() {
        return this.type;
    }
}