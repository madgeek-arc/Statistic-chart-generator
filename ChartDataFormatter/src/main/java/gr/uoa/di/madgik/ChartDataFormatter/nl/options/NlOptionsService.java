package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public void verifySignature(String library, String canonicalDescription, String sig) {
        if (!signer.verify(library, canonicalDescription, sig)) {
            throw new SecurityException("Invalid chart options signature");
        }
    }

    public JsonNode execute(String library, String canonicalDescription) {
        String cached = nlOptionsCache.get(library, canonicalDescription, promptVersion);
        if (cached != null) {
            try {
                return objectMapper.readTree(cached);
            } catch (Exception ignored) {
                // corrupt cache entry — fall through to regenerate
            }
        }
        String optionsJson = optionsGenerator.generate(library, canonicalDescription);
        nlOptionsCache.put(library, canonicalDescription, optionsJson, promptVersion);
        try {
            return objectMapper.readTree(optionsJson);
        } catch (Exception e) {
            throw new IllegalStateException("Generated options are not valid JSON: " + optionsJson, e);
        }
    }
}
