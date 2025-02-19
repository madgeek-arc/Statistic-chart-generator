package gr.uoa.di.madgik.ChartDataFormatter.Utility;

import java.math.BigDecimal;

public class NumberUtils {

    public static Number parseValue(String value) {
        try {
            if (value == null)
                return null;
            else if (value.matches("^[-+]?\\d+?([eE][-+]?\\d+)?"))
                return Long.parseLong(value);
            else if (value.matches("^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?"))
                return Double.parseDouble(value);
            else // not a number
                return null;
        } catch (NumberFormatException e) {
            return new BigDecimal(value);
        }
    }

    private NumberUtils() {
    }
}
