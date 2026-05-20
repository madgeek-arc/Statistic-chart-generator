package gr.uoa.di.madgik.ChartDataFormatter.nl.signing;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NlRequestSigner {

    private final byte[] secret;

    public NlRequestSigner(@Value("${nl.signing-secret}") String signingSecret) {
        this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Stable, deterministic serialisation of a filter list for use in HMAC and cache keys.
     * Returns "" for null/empty — so that empty filters produce the same signature as no filters.
     */
    public static String canonicalFilters(List<FilterGroup> filters) {
        if (filters == null || filters.isEmpty()) return "";
        return filters.stream()
            .map(g -> {
                String filtersStr = g.getGroupFilters().stream()
                    .sorted(Comparator.comparing(Filter::getField))
                    .map(f -> f.getField() + ":" + f.getType() + ":" +
                              f.getValues().stream().sorted().collect(Collectors.joining(",")))
                    .collect(Collectors.joining(";"));
                String op = g.getOp() != null ? "[" + g.getOp() + "]" : "";
                return filtersStr + op;
            })
            .sorted()
            .collect(Collectors.joining("|"));
    }

    public String sign(String profile, String canonicalNl) {
        return sign(profile, canonicalNl, "");
    }

    public String sign(String profile, String canonicalNl, String canonicalFilters) {
        try {
            String input = (canonicalFilters == null || canonicalFilters.isEmpty())
                ? profile + ":" + canonicalNl
                : profile + ":" + canonicalNl + "\0" + canonicalFilters;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    public boolean verify(String profile, String canonicalNl, String signature) {
        return verify(profile, canonicalNl, "", signature);
    }

    public boolean verify(String profile, String canonicalNl, String canonicalFilters, String signature) {
        return sign(profile, canonicalNl, canonicalFilters).equals(signature);
    }
}
