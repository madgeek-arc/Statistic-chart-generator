package gr.uoa.di.madgik.statstool.domain;

/**
 * Wraps a query {@link Result} with separate timing measurements:
 * <ul>
 *   <li>{@code execTimeMs} — time spent actually executing the SQL (inside the DB)</li>
 *   <li>{@code queueTimeMs} — time spent waiting in the executor queue before execution started</li>
 * </ul>
 */
public class TimedResult {
    public final Result result;
    public final int execTimeMs;
    public final int queueTimeMs;

    public TimedResult(Result result, int execTimeMs, int queueTimeMs) {
        this.result = result;
        this.execTimeMs = execTimeMs;
        this.queueTimeMs = queueTimeMs;
    }
}
