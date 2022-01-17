package gr.uoa.di.madgik.ChartDataFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedDiagrams;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
public class YamlDiagramPropertiesTest {
 
    @Autowired
    private SupportedDiagrams diagrams;
    @Test
    public void SupportedDiagramsReadFromYaml() {
        
        assertNotNull(diagrams, "Diagrams not initialized");
        assertNotNull(diagrams.getCharts(), "Supported Charts are not null");
        assertEquals(diagrams.getCharts().get(0).getSupportedLibraries().get(1), SupportedLibraries.GoogleCharts);
        assertFalse(diagrams.getCharts().get(0).isPolar);
        
        assertNull(diagrams.getPolars());
        assertNull(diagrams.getMaps());
        assertNull(diagrams.getMiscs());
        assertNull(diagrams.getSpecials());
        
    }
	
}