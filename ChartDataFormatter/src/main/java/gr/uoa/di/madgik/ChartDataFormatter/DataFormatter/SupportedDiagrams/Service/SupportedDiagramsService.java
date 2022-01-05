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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import static gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries.*;

@Service
public class SupportedDiagramsService {

    private static final String SupportedDiagramsProperties = "supportedDiagrams.properties.xml";
    private final Logger log = LogManager.getLogger(this.getClass());

    @Autowired
    private SupportedDiagrams supportedDiagrams;

    public SupportedDiagramsService() {

        // try {
        //     JAXBContext context = JAXBContext.newInstance(SupportedDiagrams.class);
            
        //     URL propertiesUrl = getClass().getResource(SupportedDiagramsProperties);

        //     Unmarshaller um = context.createUnmarshaller();
        //     this.supportedDiagrams = (SupportedDiagrams) um.unmarshal(propertiesUrl);

        // } catch (JAXBException e) {
        //     log.info("JAXB Exception, reverting to hardcoded logic");

        //     this.supportedDiagrams = new SupportedDiagrams();
            
        //     // In case there is a JAXB Exception, initialize the Supported Diagrams with the hardcoded logic
        //     this.supportedDiagrams.setSupportedCharts(this.initSupportedCharts());
        //     this.supportedDiagrams.setSupportedPolars(this.initSupportedPolars());
        //     this.supportedDiagrams.setSupportedMaps(this.initSupportedMaps());
        //     this.supportedDiagrams.setSupportedSpecialDiagrams(this.initSupportedSpecialDiagrams());
        //     this.supportedDiagrams.setSupportedMiscs(this.initSupportedMisc());

        // } catch (IllegalArgumentException e) {
        //     log.error("Supported Diagrams properties file not found");
        // } 
    }

    public List<SupportedChart> getSupportedCharts() { return this.supportedDiagrams.getSupportedCharts(); }
    public List<SupportedPolar> getSupportedPolars() { return this.supportedDiagrams.getSupportedPolars(); }
    public List<SupportedMap> getSupportedMaps() { return this.supportedDiagrams.getSupportedMaps(); }
    public List<SupportedSpecialDiagram> getSupportedSpecialDiagrams() { return this.supportedDiagrams.getSupportedSpecialDiagrams(); }
    public List<SupportedMisc> getSupportedMiscs() { return this.supportedDiagrams.getSupportedMiscs(); }


    // Old Init Methods

    private ArrayList<SupportedMap> initSupportedMaps() {
        ArrayList<SupportedMap> supportedMaps = new ArrayList<>();
        List<SupportedLibraries> mapLibs = new ArrayList<>();

        mapLibs.add(HighMaps);
        supportedMaps.add(new SupportedMap("world", "custom/world-robinson-highres", mapLibs));

        return supportedMaps;
    }

    private ArrayList<SupportedSpecialDiagram> initSupportedSpecialDiagrams() {

        ArrayList<SupportedSpecialDiagram> supportedSpecialDiagrams = new ArrayList<>();
        
        List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);
        
        supportedSpecialDiagrams.add(new SupportedSpecialDiagram("combo", chartLibs));

        return supportedSpecialDiagrams;
    }

    private ArrayList<SupportedChart> initSupportedCharts() {
        
        ArrayList<SupportedChart> supportedCharts = new ArrayList<>();
        List<SupportedLibraries> chartLibs = Arrays.asList(HighCharts, GoogleCharts, eCharts);

        for (SupportedChartTypes chartType : SupportedChartTypes.values())
            supportedCharts.add(new SupportedChart(chartType,chartLibs));
        
        return supportedCharts;
    }

    private ArrayList<SupportedPolar> initSupportedPolars() {
        ArrayList<SupportedPolar> supportedPolars = new ArrayList<>();
        
        List<SupportedLibraries> chartLibs = Arrays.asList((HighCharts));

        for (SupportedPolarTypes polarType : SupportedPolarTypes.values())
            supportedPolars.add(new SupportedPolar(polarType, chartLibs));

        return supportedPolars;
    }

    private ArrayList<SupportedMisc> initSupportedMisc() {
        ArrayList<SupportedMisc> supportedMiscs = new ArrayList<>();
        supportedMiscs.add(new SupportedMisc("numbers", Arrays.asList(HighCharts, GoogleCharts, eCharts)));

        return supportedMiscs;
    }
}

