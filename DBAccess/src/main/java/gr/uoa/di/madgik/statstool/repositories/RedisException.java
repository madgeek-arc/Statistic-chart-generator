package gr.uoa.di.madgik.statstool.repositories;

public class RedisException extends Exception {
    public RedisException(Throwable cause) {
        super(cause);
    }

    public RedisException(String message) {
        super(message);
    }
}
