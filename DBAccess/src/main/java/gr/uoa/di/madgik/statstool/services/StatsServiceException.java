package gr.uoa.di.madgik.statstool.services;

public class StatsServiceException extends Exception {
    public StatsServiceException(Throwable cause) {
        super(cause);
    }

    public StatsServiceException(String s) {
        super(s);
    }

    public StatsServiceException(String s, Throwable cause) {
        super(s, cause);
    }
}
