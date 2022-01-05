package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.List;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

public class SupportedSpecialDiagram extends SupportedDiagram{
    public String type;

    public SupportedSpecialDiagram(String type, List<SupportedLibraries> supportedLibraries) {
        this.type = type;
        this.supportedLibraries = supportedLibraries;
    }

    public void setType(String type) { this.type = type; }
    public String getType() { return this.type; }
}