package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.FileReader;
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

    private static final String SupportedDiagramsProperties = "src/main/resources/supportedDiagrams.properties.xml";
    private final Logger log = LogManager.getLogger(this.getClass());

    private SupportedDiagrams supportedDiagrams;

    public SupportedDiagramsService() {

        try {
            JAXBContext context = JAXBContext.newInstance(SupportedDiagrams.class);

            Unmarshaller um = context.createUnmarshaller();
            this.supportedDiagrams = (SupportedDiagrams) um.unmarshal(new FileReader(SupportedDiagramsProperties));

        } catch (JAXBException e) {
            log.info("JAXB Exception, reverting to hardcoded logic");

            this.supportedDiagrams = new SupportedDiagrams();
            
            // In case there is a JAXB Exception, initialize the Supported Diagrams with the hardcoded logic
            this.supportedDiagrams.setSupportedCharts(this.initSupportedCharts());
            this.supportedDiagrams.setSupportedPolars(this.initSupportedPolars());
            this.supportedDiagrams.setSupportedMaps(this.initSupportedMaps());
            this.supportedDiagrams.setSupportedSpecialDiagrams(this.initSupportedSpecialDiagrams());
            this.supportedDiagrams.setSupportedMiscs(this.initSupportedMisc());

        } catch (FileNotFoundException e) {
            log.error("Supported Diagrams properties file not found");
        } 
    }

    public List<SupportedChart> getSupportedCharts() { return this.supportedDiagrams.supportedCharts; }
    public List<SupportedPolar> getSupportedPolars() { return this.supportedDiagrams.supportedPolars; }
    public List<SupportedMap> getSupportedMaps() { return this.supportedDiagrams.supportedMaps; }
    public List<SupportedSpecialDiagram> getSupportedSpecialDiagrams() { return this.supportedDiagrams.supportedSpecialDiagrams; }
    public List<SupportedMisc> getSupportedMiscs() { return this.supportedDiagrams.supportedMiscs; }

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

    @XmlRootElement(name = "supportedDiagrams")
    public class SupportedDiagrams {

        @XmlElementWrapper()
        @XmlElement(name = "supportedChart") 
        private ArrayList<SupportedChart> supportedCharts;
        @XmlElementWrapper()
        @XmlElement(name = "supportedPolar") 
        private ArrayList<SupportedPolar> supportedPolars;
        @XmlElementWrapper()
        @XmlElement(name = "supportedMap") 
        private ArrayList<SupportedMap> supportedMaps;
        @XmlElementWrapper()
        @XmlElement(name = "supportedSpecialDiagram") 
        private ArrayList<SupportedSpecialDiagram> supportedSpecialDiagrams;
        @XmlElementWrapper()
        @XmlElement(name = "supportedMisc") 
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
    public class SupportedDiagram {
        @XmlAttribute
        public String name;
        @XmlAttribute
        public int diagramId;
        public String description;
        public String imageURL;
        public boolean isPolar;
        @XmlElementWrapper()
        @XmlElement(name = "supportedLibrary") 
        public List<SupportedLibraries> supportedLibraries;

        public SupportedDiagram() {
            this.imageURL = "images/imagePlaceholder.svg";
            this.isPolar = false;
            this.name = "";
            this.description = "";
        }

        public void setName(String name) { this.name = name; }
        public String getName() { return this.name; }

        public void setDiagramId(int diagramId) { this.diagramId = diagramId; }
        public int getdiagramId() { return this.diagramId; }
        
        public void setDescription(String description) { this.description = description; }
        public String getDescription() { return this.description; }

        public void setImageURL(String imageURL) { this.imageURL = imageURL; }
        public String getImageURL() { return this.imageURL; }

        public void setIsPolar(boolean isPolar) { this.isPolar = isPolar; }
        public boolean getIsPolar() { return this.isPolar; }

        public void setSupportedLibraries(List<SupportedLibraries> supportedLibraries) { this.supportedLibraries = supportedLibraries; }
        public List<SupportedLibraries> getSupportedLibraries() { return this.supportedLibraries; }
    }

    public class SupportedChart extends SupportedDiagram{
        public SupportedChartTypes type;

        public SupportedChart(SupportedChartTypes type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }

        public void setType(SupportedChartTypes type) { this.type = type; }
        public SupportedChartTypes getType() { return this.type; }
    }

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

    public class SupportedSpecialDiagram extends SupportedDiagram{
        public String type;

        public SupportedSpecialDiagram(String type, List<SupportedLibraries> supportedLibraries) {
            this.type = type;
            this.supportedLibraries = supportedLibraries;
        }

        public void setType(String type) { this.type = type; }
        public String getType() { return this.type; }
    }

    public class SupportedMisc extends SupportedDiagram{
        public String type;

       public SupportedMisc(String type, List<SupportedLibraries> supportedLibraries) {
           this.type = type;
           this.supportedLibraries = supportedLibraries;
       }

       public void setType(String type) { this.type = type; }
       public String getType() { return this.type; }
    }
}

