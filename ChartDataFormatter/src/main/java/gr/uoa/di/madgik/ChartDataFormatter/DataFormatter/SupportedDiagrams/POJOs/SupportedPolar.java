package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.List;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedPolarTypes;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

public class SupportedPolar extends SupportedDiagram{
    public SupportedPolarTypes type;

    public SupportedPolar(SupportedPolarTypes type, List<SupportedLibraries> supportedLibraries) {
        this.type = type;
        this.supportedLibraries = supportedLibraries;
        this.isPolar = true;
    }

    public void setType(SupportedPolarTypes type) { this.type = type; }
    public SupportedPolarTypes getType() { return this.type; }
}