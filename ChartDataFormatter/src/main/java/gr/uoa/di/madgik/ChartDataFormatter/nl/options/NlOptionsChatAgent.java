package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

public interface NlOptionsChatAgent {

    /**
     * Process one turn of the options-design conversation.
     *
     * @param sessionId   opaque session identifier
     * @param userMessage the user's latest message
     * @param library     target chart library (e.g. "HighCharts")
     * @return reply; {@code done=true} means options are finalised and signed
     */
    OptionsAgentReply chat(String sessionId, String userMessage, String library);
}
