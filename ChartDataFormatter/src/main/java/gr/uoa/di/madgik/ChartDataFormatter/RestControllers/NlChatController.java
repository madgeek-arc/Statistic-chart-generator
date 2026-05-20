package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.nl.AgentReply;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlQueryAgent;
import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/nl")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST}, origins = "*")
public class NlChatController {

    private final NlQueryAgent nlQueryAgent;
    private final ConversationSessionStore sessionStore;

    public NlChatController(NlQueryAgent nlQueryAgent, ConversationSessionStore sessionStore) {
        this.nlQueryAgent = nlQueryAgent;
        this.sessionStore = sessionStore;
    }

    @PostMapping(path = "/chat",
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() != null
                ? request.sessionId()
                : UUID.randomUUID().toString();

        if (request.profile() == null || request.message() == null) {
            return ResponseEntity.badRequest().build();
        }

        // Lazily create session on first message
        if (sessionStore.get(sessionId) == null) {
            var session = sessionStore.create(request.profile());
            sessionId = session.getSessionId();
        }

        AgentReply reply = nlQueryAgent.chat(sessionId, request.message(), request.profile());

        return ResponseEntity.ok(new ChatResponse(
                sessionId,
                reply.getReply(),
                reply.isDone(),
                reply.isDone() ? reply.getCanonicalNl() : null,
                reply.isDone() ? reply.getSig() : null,
                reply.isDone() ? reply.getSql() : null,
                reply.isDone() ? reply.getDescription() : null
        ));
    }

    public record ChatRequest(String sessionId, String profile, String message) {}

    public record ChatResponse(String sessionId, String reply, boolean done,
                               String canonicalNl, String sig, String sql, String description) {}
}
