package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries.*;

@Service
public class SupportedDiagramsService {

    private List<SupportedChart> supportedCharts;
    private List<SupportedPolar> supportedPolars;
    private List<SupportedMap> supportedMaps;
    private List<SupportedSpecialDiagram> supportedSpecialDiagrams;
    private List<SupportedMisc> supportedMiscs;

    public SupportedDiagramsService() {
        this.initSupportedCharts();
        this.initSupportedPolars();
        this.initSupportedMaps();
        this.initSupportedSpecialDiagrams();
        this.initSupportedMisc();
    }

    public List<SupportedChart> getSupportedCharts() { return supportedCharts; }
    public List<SupportedPolar> getSupportedPolars() { return supportedPolars; }
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
        List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);

        this.supportedSpecialDiagrams.add(new SupportedSpecialDiagram("combo", chartLibs));
    }

    private void initSupportedCharts() {
        this.supportedCharts = new ArrayList<>();
        List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);

        for (SupportedChartTypes chartType : SupportedChartTypes.values())
            this.supportedCharts.add(new SupportedChart(chartType,chartLibs));
    }

    private void initSupportedPolars() {
        this.supportedPolars = new ArrayList<>();
        List<SupportedLibraries> chartLibs = Arrays.asList((HighCharts));

        for (SupportedPolarTypes polarType : SupportedPolarTypes.values())
            this.supportedPolars.add(new SupportedPolar(polarType, chartLibs));
    }

    private void initSupportedMisc() {
        this.supportedMiscs = new ArrayList<>();
        this.supportedMiscs.add(new SupportedMisc("numbers", Arrays.asList(HighCharts, GoogleCharts, eCharts)));
    }

    public abstract class SupportedDiagram {
        public String name;
        public int diagramId;
        public String description;
        public String imageURL;
        public boolean isPolar;
        public List<SupportedLibraries> supportedLibraries;

        public SupportedDiagram() {
            this.imageURL = "images/imagePlaceholder.svg";
            this.isPolar = false;
            this.name = "";
            this.description = "";
        }
    }

    public class SupportedChart extends SupportedDiagram{
        public SupportedChartTypes type;

        public SupportedChart(SupportedChartTypes type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedPolar extends SupportedDiagram{
        public SupportedPolarTypes type;

        public SupportedPolar(SupportedPolarTypes type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
            this.isPolar = true;
        }
    }

    public class SupportedMap extends SupportedDiagram{
        public String type;
        public String name;

        public SupportedMap(String type, String name, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.name = name;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedSpecialDiagram extends SupportedDiagram{
        public String type;

        public SupportedSpecialDiagram(String type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }
    }

    public class SupportedMisc extends SupportedDiagram{
        public String type;

       public SupportedMisc(String type, List<SupportedLibraries> supportedLibraries) {
           this.type = type;
           this.supportedLibraries = supportedLibraries;
       }
    }
}

