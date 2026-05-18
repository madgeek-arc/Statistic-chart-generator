package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.nl.conversation.ConversationSessionStore;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.NlOptionsChatAgent;
import gr.uoa.di.madgik.ChartDataFormatter.nl.options.OptionsAgentReply;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/nl/options")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST}, origins = "*")
public class NlOptionsChatController {

    private final NlOptionsChatAgent agent;
    private final ConversationSessionStore sessionStore;

    public NlOptionsChatController(NlOptionsChatAgent agent, ConversationSessionStore sessionStore) {
        this.agent = agent;
        this.sessionStore = sessionStore;
    }

    @PostMapping(path = "/chat",
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    public ResponseEntity<OptionsResponse> chat(@RequestBody OptionsRequest request) {
        if (request.library() == null || request.message() == null) {
            return ResponseEntity.badRequest().build();
        }

        String sessionId = request.sessionId() != null
                ? request.sessionId()
                : UUID.randomUUID().toString();

        if (sessionStore.get(sessionId) == null) {
            var session = sessionStore.create(request.library());
            sessionId = session.getSessionId();
        }

        OptionsAgentReply reply = agent.chat(sessionId, request.message(), request.library());

        return ResponseEntity.ok(new OptionsResponse(
                sessionId,
                reply.getReply(),
                reply.isDone(),
                reply.isDone() ? reply.getCanonicalDescription() : null,
                reply.isDone() ? reply.getSig() : null,
                reply.isDone() ? reply.getOptionsJson() : null
        ));
    }

    public record OptionsRequest(String sessionId, String library, String message) {}

    public record OptionsResponse(String sessionId, String reply, boolean done,
                                   String canonicalDescription, String sig, String optionsJson) {}
}
