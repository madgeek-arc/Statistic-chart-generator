package gr.uoa.di.madgik.ChartDataFormatter.nl;

public class AgentReply {
    private final String reply;
    private final boolean done;
    private final String canonicalNl;

    public AgentReply(String reply, boolean done, String canonicalNl) {
        this.reply = reply;
        this.done = done;
        this.canonicalNl = canonicalNl;
    }

    public String getReply() { return reply; }
    public boolean isDone() { return done; }
    public String getCanonicalNl() { return canonicalNl; }
}
