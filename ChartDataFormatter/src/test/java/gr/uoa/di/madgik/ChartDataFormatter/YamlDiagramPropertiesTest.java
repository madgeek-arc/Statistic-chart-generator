package gr.uoa.di.madgik.ChartDataFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedChart;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs.SupportedDiagrams;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@TestPropertySource("classpath:application.yml")
@RunWith(SpringRunner.class)
public class YamlDiagramPropertiesTest {
 
    @Autowired
    private SupportedDiagrams diagrams;

    @Configuration
    @EnableConfigurationProperties(value = SupportedDiagrams.class)
    @PropertySource("classpath:application.yml")
    static class ContextConfiguration {
    }



    @Test
    public void SupportedDiagramsReadFromYaml() {
        
        assertNotNull(diagrams, "Diagrams not initialized");
        assertNotNull(diagrams.getCharts(), "Supported Charts are not null");
        SupportedChart firstChart = diagrams.getCharts().get(0);
        assertEquals(firstChart.getSupportedLibraries().get(1), SupportedLibraries.GoogleCharts);
        assertFalse(diagrams.getCharts().get(0).isPolar);
        assertNotNull(diagrams.getCharts().get(0).getType(), "Type is not null");
        assertNotNull(diagrams.getCharts().get(0).getType(), "column");

        assertFalse(diagrams.getPolars().isEmpty());
        assertFalse(diagrams.getMaps().isEmpty());
        assertFalse(diagrams.getMiscs().isEmpty());
        assertFalse(diagrams.getSpecials().isEmpty());
    }
	
}