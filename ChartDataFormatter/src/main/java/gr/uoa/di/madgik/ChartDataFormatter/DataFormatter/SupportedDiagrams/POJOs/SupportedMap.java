package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.List;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

public class SupportedMap extends SupportedDiagram{
    public String type;
    public String name;

    public SupportedMap(String type, String name, List<SupportedLibraries> supportedLibraries) {
        this.type = type;
        this.name = name;
        this.supportedLibraries = supportedLibraries;
    }

    public void setType(String type) { this.type = type; }
    public String getType() { return this.type; }
}