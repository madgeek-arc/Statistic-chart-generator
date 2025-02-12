package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An abstract class that should be extended by each Chart Library Formatter.
 */
public abstract class DataFormatter {
    /**
     * Objects of the dbAccessResults and chartsType Lists are <i>supposed</i> to be matched 1-1.
     *
     * @param dbAccessResults A List of {@link Result} originating from DBAccess.
     * @param args            Optional parameters for the implementations of the class to take advantage of.
     *                        Such an optional parameter could be a List with the types of Chart that the List of Results will be formatted into.
     * @return The return object should follow the guidelines of {@link JsonResponse}.
     */
    public abstract JsonResponse toJsonResponse(List<Result> dbAccessResults, Object... args) throws DataFormationException;

    public List<String> getXAxisCategories(List<Result> dbAccessResults, boolean sort) {

        //A HashSet with all the possible x values occurring from the Queries.
        LinkedHashSet<String> xAxis_categories = new LinkedHashSet<>();

        for (Result result : dbAccessResults) {

            if (result.getRows().isEmpty())
                continue;

            for (List<?> row : result.getRows()) {
                // Get the first groupBy of the result row
                String xValue = valueToString(row.get(1));

                //Find a xAxis value and register it in the xAxis_categories
                if (!xAxis_categories.contains(xValue))
                    xAxis_categories.add(xValue);
            }
        }

        ArrayList<String> xAxis_Categories = new ArrayList<>(xAxis_categories);


        if (sort)
            xAxis_Categories.sort(String::compareToIgnoreCase);

        return xAxis_Categories;
    }

    /**
     * Translates values to plain String avoiding scientific formatting.
     *
     * @param obj the {@link Object} holding the value
     * @return the value in plain string format
     */
    public String valueToString(Object obj) {
        if (obj instanceof Double) {
            return BigDecimal.valueOf((double) obj).toPlainString();
        } else if (obj instanceof Float) {
            return BigDecimal.valueOf((float) obj).toPlainString();
        } else {
            return String.valueOf(obj);
        }
    }

    /**
     * An exception signifying an error in the process of Data Formation.
     */
    public class DataFormationException extends Exception {

        public DataFormationException() {
        }

        public DataFormationException(String s) {
            super(s);
        }

        public DataFormationException(String s, Throwable throwable) {
            super(s, throwable);
        }

        public DataFormationException(Throwable throwable) {
            super(throwable);
        }

        public DataFormationException(String s, Throwable throwable, boolean b, boolean b1) {
            super(s, throwable, b, b1);
        }
    }
}
