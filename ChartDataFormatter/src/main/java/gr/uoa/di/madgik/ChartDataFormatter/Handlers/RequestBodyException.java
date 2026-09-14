package gr.uoa.di.madgik.ChartDataFormatter.Handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * An exception holding a HttpStatus code along with the error message.
 */
public class RequestBodyException extends Exception {

    private final HttpStatus httpStatus;

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    // 400 responses carry the specific reason in the body; other statuses stay status-only.
    public static ResponseEntity<?> toResponseEntity(RequestBodyException e) {
        if (e.getHttpStatus() == HttpStatus.BAD_REQUEST) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), e.getHttpStatus());
        }
        return new ResponseEntity<>(e.getHttpStatus());
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
