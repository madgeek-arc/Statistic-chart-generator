package gr.uoa.di.madgik.ChartDataFormatter.nl.signing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class NlRequestSigner {

    private final byte[] secret;

    public NlRequestSigner(@Value("${nl.signing-secret}") String signingSecret) {
        this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String profile, String canonicalNl) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal((profile + ":" + canonicalNl).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    public boolean verify(String profile, String canonicalNl, String signature) {
        return sign(profile, canonicalNl).equals(signature);
    }
}
