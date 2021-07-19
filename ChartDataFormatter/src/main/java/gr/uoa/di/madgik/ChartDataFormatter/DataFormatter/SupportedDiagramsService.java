package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes.*;
import static gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries.*;

@Service
public class SupportedDiagramsService {

    private List<SupportedChart> supportedCharts;
    private List<SupportedMap> supportedMaps;
    private List<SupportedSpecialDiagram> supportedSpecialDiagrams;
    private List<SupportedMisc> supportedMiscs;

    public SupportedDiagramsService() {
        this.initSupportedCharts();
        this.initSupportedMaps();
        this.initSupportedSpecialDiagrams();
        this.initSupportedMisc();
    }

    public List<SupportedChart> getSupportedCharts() { return supportedCharts; }
    public List<SupportedMap> getSupportedMaps() { return supportedMaps; }
    public List<SupportedSpecialDiagram> getSupportedSpecialDiagrams() { return supportedSpecialDiagrams; }
    public List<SupportedMisc> getSupportedMiscs() { return this.supportedMiscs; }

    private void initSupportedMaps() {
        this.supportedMaps = new ArrayList<>();
        List<SupportedLibraries> mapLibs = new ArrayList<>();
        mapLibs.add(HighMaps);
        this.supportedMaps.add(new SupportedMap("world", "custom/world-robinson-highres", mapLibs));
    }

    private void initSupportedSpecialDiagrams() {
        this.supportedSpecialDiagrams = new ArrayList<>();
        List<SupportedLibraries> chartLibs = new ArrayList<>();
        chartLibs.add(HighCharts);
        chartLibs.add(GoogleCharts);
        chartLibs.add(eCharts);
        this.supportedSpecialDiagrams.add(new SupportedSpecialDiagram("combo", chartLibs));

    }

    private void initSupportedCharts() {
        this.supportedCharts = new ArrayList<>();
        List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);

        this.supportedCharts.add(new SupportedChart(area,chartLibs));
        this.supportedCharts.add(new SupportedChart(pie,chartLibs));
        this.supportedCharts.add(new SupportedChart(column,chartLibs));
        this.supportedCharts.add(new SupportedChart(bar,chartLibs));
        this.supportedCharts.add(new SupportedChart(line,chartLibs));

        this.supportedCharts.add(new SupportedChart(spider, Arrays.asList(HighCharts)));
    }

    private void initSupportedMisc() {
        this.supportedMiscs = new ArrayList<>();
        this.supportedMiscs.add(new SupportedMisc("numbers", Arrays.asList(HighCharts, GoogleCharts, eCharts)));
    }

    public class SupportedChart {
        public SupportedChartTypes type;
        public List<SupportedLibraries> supportedLibraries;

        public SupportedChart(SupportedChartTypes type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedMap {
        public String type;
        public String name;
        public List<SupportedLibraries>  supportedLibraries;

        public SupportedMap(String type, String name, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.name = name;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedSpecialDiagram {
        public String type;
        public List<SupportedLibraries> supportedLibraries;

        public SupportedSpecialDiagram(String type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedMisc {
        public String type;
        public List<SupportedLibraries> supportedLibraries;

       public SupportedMisc(String type, List<SupportedLibraries> supportedLibraries) {
           this.type = type;
           this.supportedLibraries = supportedLibraries;
       }
    }
}

