package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NlOptionsService {

    private final NlOptionsGenerator optionsGenerator;
    private final NlOptionsCache nlOptionsCache;
    private final NlRequestSigner signer;
    private final String promptVersion;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NlOptionsService(NlOptionsGenerator optionsGenerator,
                            NlOptionsCache nlOptionsCache,
                            NlRequestSigner signer,
                            @Value("${nl.options-prompt-version:1}") String promptVersion) {
        this.optionsGenerator = optionsGenerator;
        this.nlOptionsCache = nlOptionsCache;
        this.signer = signer;
        this.promptVersion = promptVersion;
    }

    // title.text and subtitle.text are always controlled by the caller — strip them so
    // a LLM-generated placeholder never clobbers the chart's real title.
    private void stripContentFields(JsonNode node) {
        for (String key : new String[]{"title", "subtitle"}) {
            JsonNode section = node.get(key);
            if (section instanceof ObjectNode) {
                ((ObjectNode) section).remove("text");
                if (!section.fields().hasNext()) {
                    ((ObjectNode) node).remove(key);
                }
            }
        }
    }

    public void verifySignature(String library, String canonicalDescription, String sig) {
        if (!signer.verify(library, canonicalDescription, sig)) {
            throw new SecurityException("Invalid chart options signature");
        }
    }

    public JsonNode execute(String library, String canonicalDescription) {
        String cached = nlOptionsCache.get(library, canonicalDescription, promptVersion);
        if (cached != null) {
            try {
                JsonNode node = objectMapper.readTree(cached);
                stripContentFields(node);
                return node;
            } catch (Exception ignored) {
                // corrupt cache entry — fall through to regenerate
            }
        }
        String optionsJson = optionsGenerator.generate(library, canonicalDescription);
        nlOptionsCache.put(library, canonicalDescription, optionsJson, promptVersion);
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            stripContentFields(node);
            return node;
        } catch (Exception e) {
            throw new IllegalStateException("Generated options are not valid JSON: " + optionsJson, e);
        }
    }
}
