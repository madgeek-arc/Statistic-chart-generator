package gr.uoa.di.madgik.ChartDataFormatter.nl;

public class AgentReply {
    private final String reply;
    private final boolean done;
    private final String canonicalNl;
    private final String sig;
    private final String sql;

    public AgentReply(String reply, boolean done, String canonicalNl, String sig, String sql) {
        this.reply = reply;
        this.done = done;
        this.canonicalNl = canonicalNl;
        this.sig = sig;
        this.sql = sql;
    }

    public String getReply() { return reply; }
    public boolean isDone() { return done; }
    public String getCanonicalNl() { return canonicalNl; }
    public String getSig() { return sig; }
    public String getSql() { return sql; }
}
