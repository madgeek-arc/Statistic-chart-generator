package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedPolarTypes;

public class SupportedPolar extends SupportedDiagram {
    private SupportedPolarTypes type;

    public void setType(SupportedPolarTypes type) {
        this.type = type;
    }

    public SupportedPolarTypes getType() {
        return this.type;
    }
}