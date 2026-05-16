package gr.uoa.di.madgik.ChartDataFormatter.nl;

public interface NlQueryAgent {

    /**
     * Process one turn of the conversation.
     *
     * @param sessionId   opaque session identifier, created by the caller on first turn
     * @param userMessage the user's latest message
     * @param profile     profile name (e.g. "openaire")
     * @return reply carrying the agent's response; {@code done=true} means the
     *         canonical NL is finalised and the URL has been signed
     */
    AgentReply chat(String sessionId, String userMessage, String profile);
}
