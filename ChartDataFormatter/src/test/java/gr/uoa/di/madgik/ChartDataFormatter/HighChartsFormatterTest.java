package gr.uoa.di.madgik.ChartDataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.DataFormatter.DataFormationException;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.HighChartsDataFormatter;
import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.HighChartsJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HighChartsFormatterTest {

    private static Result resultOf(List<?>... rows) {
        Result r = new Result();
        List<List<?>> rowList = new ArrayList<>();
        for (List<?> row : rows) rowList.add(row);
        r.setRows(rowList);
        return r;
    }

    @Test
    public void scalarResult_numbersChartType_returnsValueWithNoXAxis() throws DataFormationException {
        // Regression: "numbers" chart type maps to null in SupportedChartTypes; a single aggregate
        // with no GROUP BY produces a row of size 1. Formatter must return the value, not throw.
        Result scalar = resultOf(Collections.singletonList("42350"));

        HighChartsJsonResponse response = new HighChartsDataFormatter().toJsonResponse(
                Collections.singletonList(scalar),
                Collections.singletonList((SupportedChartTypes) null),
                Collections.singletonList("Total Results"),
                false);

        assertNotNull(response);
        assertNotNull(response.getDataSeries());
        assertFalse(response.getDataSeries().isEmpty(), "dataSeries must not be empty");
        assertTrue(response.getxAxis_categories() == null || response.getxAxis_categories().isEmpty(),
                "x-axis categories must be empty for a scalar result");
        assertTrue(response.getDataSeriesTypes().contains("numbers"),
                "series type must be 'numbers'; got: " + response.getDataSeriesTypes());
    }

    @Test
    public void scalarResult_emptyRows_returnsEmptySeriesNotThrow() throws DataFormationException {
        // Empty result for a scalar/numbers query (chartType=null) must not throw NPE.
        Result empty = resultOf();

        HighChartsJsonResponse response = new HighChartsDataFormatter().toJsonResponse(
                Collections.singletonList(empty),
                Collections.singletonList((SupportedChartTypes) null),
                Collections.singletonList("Total Results"),
                false);

        assertNotNull(response);
        assertNotNull(response.getDataSeries());
    }

    @Test
    public void singleGroupBy_twoColumnRow_returnsSeriesAndXAxis() throws DataFormationException {
        // Baseline: normal [count, category] rows produce a series with matching x-axis.
        Result r = resultOf(
                Arrays.asList("120", "Software"),
                Arrays.asList("85",  "Dataset"));

        HighChartsJsonResponse response = new HighChartsDataFormatter().toJsonResponse(
                Collections.singletonList(r),
                Collections.singletonList(SupportedChartTypes.bar),
                Collections.singletonList("By Type"),
                false);

        assertNotNull(response);
        assertFalse(response.getDataSeries().isEmpty());
        assertEquals(2, response.getxAxis_categories().size());
        assertTrue(response.getxAxis_categories().contains("Software"));
        assertTrue(response.getxAxis_categories().contains("Dataset"));
    }
}
