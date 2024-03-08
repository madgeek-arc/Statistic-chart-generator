package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("statstool.supported-diagrams")
public class SupportedDiagrams {

    private List<SupportedChart> charts = new ArrayList<>();
    private List<SupportedPolar> polars = new ArrayList<>();
    private List<SupportedMap> maps = new ArrayList<>();
    private List<SupportedSpecialDiagram> specials = new ArrayList<>();
    private List<SupportedMisc> miscs = new ArrayList<>();

    public void setCharts(List<SupportedChart> supportedCharts) {
        this.charts = supportedCharts;
    }

    public void setPolars(List<SupportedPolar> supportedPolars) {
        this.polars = supportedPolars;
    }

    public void setMaps(List<SupportedMap> supportedMaps) {
        this.maps = supportedMaps;
    }

    public void setSpecials(List<SupportedSpecialDiagram> supportedSpecialDiagrams) {
        this.specials = supportedSpecialDiagrams;
    }

    public void setMiscs(List<SupportedMisc> supportedMiscs) {
        this.miscs = supportedMiscs;
    }

    public List<SupportedChart> getCharts() {
        return this.charts;
    }

    public List<SupportedPolar> getPolars() {
        return this.polars;
    }

    public List<SupportedMap> getMaps() {
        return this.maps;
    }

    public List<SupportedSpecialDiagram> getSpecials() {
        return this.specials;
    }

    public List<SupportedMisc> getMiscs() {
        return this.miscs;
    }
}