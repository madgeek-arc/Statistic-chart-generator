package gr.uoa.di.madgik.ChartDataFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedDiagrams;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = SupportedDiagrams.class)
public class YamlDiagramPropertiesTest {
 
    @Autowired
    private SupportedDiagrams diagrams;
 
    @Test
    public void whenYamlNestedLists_thenLoadComplexLists() {
        
        assertNotNull(diagrams, "Diagrams not initialized");
        assertNotNull(diagrams.getSupportedCharts(), "Supported Charts are not null");
        assertEquals(diagrams.getSupportedCharts().get(0).getSupportedLibraries().get(1), SupportedLibraries.GoogleCharts);
        assertFalse(diagrams.getSupportedCharts().get(0).isPolar);
        
        assertNull(diagrams.getSupportedPolars());
        assertNull(diagrams.getSupportedMaps());
        assertNull(diagrams.getSupportedMiscs());
        assertNull(diagrams.getSupportedSpecialDiagrams());
        
    }
	
}