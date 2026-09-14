package gr.uoa.di.madgik.statstool.services;

/**
 * Signals invalid {@code namedParameters} input on a named query - e.g. both {@code parameters}
 * and {@code namedParameters} supplied, an empty-list value, or a {@code :name} token with no
 * matching entry. A distinct type so callers can identify this specific class of error.
 */
public class NamedParametersValidationException extends RuntimeException {
    public NamedParametersValidationException(String message) {
        super(message);
    }
}
