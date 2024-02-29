package gr.uoa.di.madgik.ChartDataFormatter.Utility;

public class NumberUtils {

    public static Number parseValue(String yValue) {
        if(yValue == null)
            return null;
        else if (yValue.matches("[0-9.,]*"))
            return Float.parseFloat(yValue);
        else if (yValue.matches("\\d*"))
            return Integer.parseInt(yValue);
        else
            return null;
    }

    private NumberUtils() {
    }
}
