package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.List;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

public class SupportedChart extends SupportedDiagram{
    public SupportedChartTypes type;

    public SupportedChart(SupportedChartTypes type, List<SupportedLibraries> supportedLibraries) {
        this.type = type;
        this.supportedLibraries = supportedLibraries;
    }

    public void setType(SupportedChartTypes type) { this.type = type; }
    public SupportedChartTypes getType() { return this.type; }
}