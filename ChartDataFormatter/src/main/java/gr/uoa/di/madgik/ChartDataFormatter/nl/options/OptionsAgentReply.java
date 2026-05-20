package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

public class OptionsAgentReply {

    private final String reply;
    private final boolean done;
    private final String canonicalDescription;
    private final String sig;
    private final String optionsJson;

    public OptionsAgentReply(String reply, boolean done,
                             String canonicalDescription, String sig, String optionsJson) {
        this.reply = reply;
        this.done = done;
        this.canonicalDescription = canonicalDescription;
        this.sig = sig;
        this.optionsJson = optionsJson;
    }

    public String getReply() { return reply; }
    public boolean isDone() { return done; }
    public String getCanonicalDescription() { return canonicalDescription; }
    public String getSig() { return sig; }
    public String getOptionsJson() { return optionsJson; }
}
