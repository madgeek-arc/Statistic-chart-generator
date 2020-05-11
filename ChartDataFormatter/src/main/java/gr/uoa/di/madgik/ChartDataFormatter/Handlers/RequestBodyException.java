package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import org.springframework.http.HttpStatus;

/**
 * An exception holding a HttpStatus code along with the error message.
 */
public class RequestBodyException extends Exception {

    private final HttpStatus httpStatus;

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public RequestBodyException(HttpStatus httpStatus) {
        super();
        this.httpStatus = httpStatus;
    }

    public RequestBodyException(String s, HttpStatus httpStatus) {
        super(s);
        this.httpStatus = httpStatus;
    }

    public RequestBodyException(String s, Throwable throwable, HttpStatus httpStatus) {
        super(s, throwable);
        this.httpStatus = httpStatus;
    }

    public RequestBodyException(Throwable throwable, HttpStatus httpStatus) {
        super(throwable);
        this.httpStatus = httpStatus;
    }

}
