package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.Service;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedPolarTypes;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedChart;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedDiagrams;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedMap;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedMisc;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedPolar;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedSpecialDiagram;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries.*;

@Service
public class SupportedDiagramsService {

    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired
    private SupportedDiagrams supportedDiagrams;

    // public SupportedDiagramsService() {

    //     if(this.supportedDiagrams == null)
    //     {
    //         log.error("Supported Diagrams could not be read by the YAML file. Back up initialization used");
    //         this.supportedDiagrams = new SupportedDiagrams();

    //         this.supportedDiagrams.setCharts(initSupportedCharts());
    //         this.supportedDiagrams.setMaps(initSupportedMaps());
    //         this.supportedDiagrams.setMiscs(initSupportedMisc());
    //         this.supportedDiagrams.setPolars(initSupportedPolars());
    //         this.supportedDiagrams.setSpecials(initSupportedSpecialDiagrams());
    //     }

    // }

    public SupportedDiagrams getSupportedDiagrams() { return this.supportedDiagrams; }
    public List<SupportedChart> getSupportedCharts() { return this.supportedDiagrams.getCharts(); }
    public List<SupportedPolar> getSupportedPolars() { return this.supportedDiagrams.getPolars(); }
    public List<SupportedMap> getSupportedMaps() { return this.supportedDiagrams.getMaps(); }
    public List<SupportedSpecialDiagram> getSupportedSpecialDiagrams() { return this.supportedDiagrams.getSpecials(); }
    public List<SupportedMisc> getSupportedMiscs() { return this.supportedDiagrams.getMiscs(); }


    // Backup Initialization

    // private ArrayList<SupportedMap> initSupportedMaps() {
    //     ArrayList<SupportedMap> supportedMaps = new ArrayList<>();
    //     List<SupportedLibraries> mapLibs = new ArrayList<>();

    //     mapLibs.add(HighMaps);
    //     supportedMaps.add(new SupportedMap("world", "custom/world-robinson-highres", mapLibs));

    //     return supportedMaps;
    // }

    // private ArrayList<SupportedSpecialDiagram> initSupportedSpecialDiagrams() {

    //     ArrayList<SupportedSpecialDiagram> supportedSpecialDiagrams = new ArrayList<>();
        
    //     List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);
        
    //     supportedSpecialDiagrams.add(new SupportedSpecialDiagram("combo", chartLibs));

    //     return supportedSpecialDiagrams;
    // }

    // private ArrayList<SupportedChart> initSupportedCharts() {
        
    //     ArrayList<SupportedChart> supportedCharts = new ArrayList<>();
    //     List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);

    //     for (SupportedChartTypes chartType : SupportedChartTypes.values())
    //         supportedCharts.add(new SupportedChart(chartType,chartLibs));
        
    //     return supportedCharts;
    // }

    // private ArrayList<SupportedPolar> initSupportedPolars() {
    //     ArrayList<SupportedPolar> supportedPolars = new ArrayList<>();
        
    //     List<SupportedLibraries> chartLibs = Arrays.asList((HighCharts));

    //     for (SupportedPolarTypes polarType : SupportedPolarTypes.values())
    //         supportedPolars.add(new SupportedPolar(polarType, chartLibs));

    //     return supportedPolars;
    // }

    // private ArrayList<SupportedMisc> initSupportedMisc() {
    //     ArrayList<SupportedMisc> supportedMiscs = new ArrayList<>();
    //     supportedMiscs.add(new SupportedMisc("numbers", Arrays.asList(HighCharts, GoogleCharts, eCharts)));

    //     return supportedMiscs;
    // }
}

