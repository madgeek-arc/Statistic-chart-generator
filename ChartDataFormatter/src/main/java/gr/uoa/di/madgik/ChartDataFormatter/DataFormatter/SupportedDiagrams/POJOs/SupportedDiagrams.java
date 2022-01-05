package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.ArrayList;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supportedDiagrams")
public class SupportedDiagrams {

    private ArrayList<SupportedChart> supportedCharts;
    private ArrayList<SupportedPolar> supportedPolars;
    private ArrayList<SupportedMap> supportedMaps;
    private ArrayList<SupportedSpecialDiagram> supportedSpecialDiagrams;
    private ArrayList<SupportedMisc> supportedMiscs;

    public void setSupportedCharts(ArrayList<SupportedChart> supportedCharts) { this.supportedCharts = supportedCharts; }
    public void setSupportedPolars(ArrayList<SupportedPolar> supportedPolars) { this.supportedPolars = supportedPolars; }
    public void setSupportedMaps(ArrayList<SupportedMap> supportedMaps) { this.supportedMaps = supportedMaps; }
    public void setSupportedSpecialDiagrams(ArrayList<SupportedSpecialDiagram> supportedSpecialDiagrams) { this.supportedSpecialDiagrams = supportedSpecialDiagrams; }
    public void setSupportedMiscs(ArrayList<SupportedMisc> supportedMiscs) { this.supportedMiscs = supportedMiscs; }

    public ArrayList<SupportedChart> getSupportedCharts() { return this.supportedCharts; }
    public ArrayList<SupportedPolar> getSupportedPolars() { return this.supportedPolars; }
    public ArrayList<SupportedMap> getSupportedMaps() { return this.supportedMaps; }
    public ArrayList<SupportedSpecialDiagram> getSupportedSpecialDiagrams() { return this.supportedSpecialDiagrams; }
    public ArrayList<SupportedMisc> getSupportedMiscs() { return this.supportedMiscs; }
}