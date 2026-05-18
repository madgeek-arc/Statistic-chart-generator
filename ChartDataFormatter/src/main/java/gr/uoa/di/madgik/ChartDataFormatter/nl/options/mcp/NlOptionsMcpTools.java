package gr.uoa.di.madgik.ChartDataFormatter.nl.options.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.repositories.NlOptionsCache;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NlOptionsMcpTools {

    public record SignedOptions(String library, String canonicalDescription, String sig, String optionsJson) {}

    private static final ThreadLocal<SignedOptions> pendingSign = new ThreadLocal<>();

    public static void clearSignedOptions() { pendingSign.remove(); }

    public static SignedOptions consumeSignedOptions() {
        SignedOptions result = pendingSign.get();
        pendingSign.remove();
        return result;
    }

    private final NlRequestSigner signer;
    private final NlOptionsCache nlOptionsCache;
    private final String promptVersion;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NlOptionsMcpTools(NlRequestSigner signer,
                             NlOptionsCache nlOptionsCache,
                             @Value("${nl.options-prompt-version:1}") String promptVersion) {
        this.signer = signer;
        this.nlOptionsCache = nlOptionsCache;
        this.promptVersion = promptVersion;
    }

    @Tool(description = "Validates the options JSON structure and returns it formatted for the user to review. Returns the pretty-printed JSON or an error message.")
    public String previewOptions(String optionsJson) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(optionsJson));
        } catch (Exception e) {
            return "INVALID JSON: " + e.getMessage() + ". Please correct the JSON and try again.";
        }
    }

    @Tool(description = "Signs and caches the final chart options. Call this when the user is satisfied with the chart appearance.")
    public String signChartOptions(String library, String canonicalDescription, String optionsJson) {
        try {
            objectMapper.readTree(optionsJson);
        } catch (Exception e) {
            return "ERROR: Invalid JSON: " + e.getMessage();
        }
        nlOptionsCache.put(library, canonicalDescription, optionsJson, promptVersion);
        String sig = signer.sign(library, canonicalDescription);
        pendingSign.set(new SignedOptions(library, canonicalDescription, sig, optionsJson));
        return "Chart options signed successfully.";
    }
}
