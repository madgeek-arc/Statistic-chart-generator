package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.JsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;
import org.springframework.lang.NonNull;

import java.util.*;

/**
 * An abstract class that should be extended by each Chart Library Formatter.
 */
public abstract class DataFormatter {
    /**
     * Objects of the dbAccessResults and chartsType Lists are <i>supposed</i> to be matched 1-1.
     *
     * @param dbAccessResults A List of {@link Result} originating from DBAccess.
     * @param chartsType A List with the types of Chart that the List of Results will be formatted into.
     *                  Each type of this List should be one of {@link SupportedChartTypes}.
     * @return The return object should follow the guidelines of {@link JsonResponse}.
     */
    public abstract JsonResponse toJsonResponse(@NonNull List<Result> dbAccessResults,
                                                @NonNull List<SupportedChartTypes> chartsType) throws DataFormationException;

    /**
     * An exception signifying an error in the process of Data Formation.
     */
    public class DataFormationException extends Exception{

        public DataFormationException() { }
        public DataFormationException(String s)
        { super(s); }
        public DataFormationException(String s, Throwable throwable)
        { super(s, throwable); }
        public DataFormationException(Throwable throwable)
        { super(throwable); }
        public DataFormationException(String s, Throwable throwable, boolean b, boolean b1)
        { super(s, throwable, b, b1); }
    }
}
