package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonChartRepresentation.JsonResponse;
import gr.uoa.di.madgik.statstool.db.Result;
import org.springframework.lang.NonNull;

import java.util.*;

/**
 * An abstract class that should be extended by each Chart Library Formatter.
 */
public abstract class DataFormatter {
    /**
     *
     *
     * @param dbAccessResults A List of {@link Result} originating from DBAccess.
     * @param chartType The type of Chart that the List of Results will be formatted into.
     *                  This type of Chart should be one of {@link SupportedChartTypes}.
     * @return The return object should follow the guidelines of {@link JsonResponse}.
     */
    public abstract JsonResponse toJsonResponse(@NonNull List<Result> dbAccessResults,@NonNull SupportedChartTypes chartType);

}
