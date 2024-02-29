package gr.uoa.di.madgik.ChartDataFormatter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;


import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.EChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.AbsData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.HighChartsDataRepresentation.GraphData;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.EChartsJsonResponse;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;

@SpringBootTest
public class GraphResponseTest {
    
    protected Result demoResult;
    
    @Before
    public void setUp() {
        this.demoResult = new Result();

        List<List<?>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(Arrays.asList("FI", "69270", "BD", "3")));
        rows.add(new ArrayList<>(Arrays.asList("EG","175","NZ","1")));
        rows.add(new ArrayList<>(Arrays.asList("NZ","262","FI","14")));
        rows.add(new ArrayList<>(Arrays.asList("LT","810","MY","3")));
        rows.add(new ArrayList<>(Arrays.asList("NG","57","TG","1")));
        rows.add(new ArrayList<>(Arrays.asList("CH","64922","GL","2")));

        this.demoResult.setRows(rows);
    }

    @Test
    public void HCGraphResponseTest() throws DataFormatter.DataFormationException {
        
        List<SupportedChartTypes> chartTypes = new ArrayList<SupportedChartTypes>();
        chartTypes.add(SupportedChartTypes.dependencywheel);
        List<String> chartNames = new ArrayList<String>();
        chartNames.add("TestName");

        List<Result> resultList = new ArrayList<Result>();
        resultList.add(demoResult);

        Result result = resultList.get(0);
        boolean ignoreNodeWeight = true;

        // Initialize the keys array
        List<String> keys = Arrays.asList("from", "to", "weight");

        // Initialize the data array
        ArrayList<Object[]> data = new ArrayList<>(result.getRows().size());

        for (List<?> row : result.getRows()) {
            
            // Ignore the 'from' node weight
            if(ignoreNodeWeight)
            {
                assertTrue(keys.size() > 0);
                // Initialize each data row with exactly the size of the keys
                ArrayList<Object> dataRow = new ArrayList<>();

                dataRow.add(row.get(0));
                dataRow.add(row.get(2));
                dataRow.add(row.get(3));

                assertFalse(dataRow.isEmpty());
                assertTrue(dataRow.size() == keys.size());
                // Push it into data list
                data.add(dataRow.stream().toArray());
                continue;
            }

            // Push the row into the data list
            data.add(row.stream().toArray());
        }
        assertFalse(data.isEmpty());
        assertTrue(keys.size() == 3);
        // Create the graph
        GraphData graph = new GraphData(keys, data);

        // Fill the HighChartsJsonResponse
        ArrayList<AbsData> dataSeries = new ArrayList<>();
        dataSeries.add(graph);
        // ArrayList<String> chartNames = new ArrayList<>();
        // chartNames.add(chartName);
        // ArrayList<String> chartTypes = new ArrayList<>();
        // chartTypes.add(chartType.name());



        // HighChartsJsonResponse response = new HighChartsDataFormatter().toJsonResponse(resultList, chartsType, chartNames);
        
    }

    @Test
    public void ECGraphResponseTest() throws DataFormatter.DataFormationException {
        
        List<SupportedChartTypes> chartsType = new ArrayList<SupportedChartTypes>();
        chartsType.add(SupportedChartTypes.dependencywheel);
        List<String> chartNames = new ArrayList<String>();
        chartNames.add("TestName");

        List<Result> resultList = new ArrayList<Result>();
        resultList.add(demoResult);

        EChartsJsonResponse response = new EChartsDataFormatter().toJsonResponse(resultList, chartsType, chartNames);
    
    }
}
