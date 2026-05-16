package gr.uoa.di.madgik.ChartDataFormatter.nl.signing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NlRequestSignerTest {

    private NlRequestSigner signer;

    @BeforeEach
    void setup() {
        signer = new NlRequestSigner("test-secret-key");
    }

    @Test
    void sign_returnsNonNullHexString() {
        String sig = signer.sign("openaire", "publications per year");
        assertNotNull(sig);
        assertTrue(sig.matches("[0-9a-f]{64}"), "Expected 64-char hex string, got: " + sig);
    }

    @Test
    void sign_isDeterministic() {
        String sig1 = signer.sign("openaire", "publications per year");
        String sig2 = signer.sign("openaire", "publications per year");
        assertEquals(sig1, sig2);
    }

    @Test
    void verify_returnsTrueForCorrectSignature() {
        String sig = signer.sign("openaire", "publications per year");
        assertTrue(signer.verify("openaire", "publications per year", sig));
    }

    @Test
    void verify_returnsFalseForWrongSignature() {
        assertFalse(signer.verify("openaire", "publications per year", "deadbeef"));
    }

    @Test
    void verify_returnsFalseForWrongProfile() {
        String sig = signer.sign("openaire", "publications per year");
        assertFalse(signer.verify("monitor", "publications per year", sig));
    }

    @Test
    void verify_returnsFalseForWrongNl() {
        String sig = signer.sign("openaire", "publications per year");
        assertFalse(signer.verify("openaire", "datasets per year", sig));
    }

    @Test
    void differentSecrets_produceDifferentSignatures() {
        NlRequestSigner other = new NlRequestSigner("different-secret");
        String sig1 = signer.sign("openaire", "nl");
        String sig2 = other.sign("openaire", "nl");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void differentProfiles_produceDifferentSignatures() {
        String sig1 = signer.sign("openaire", "nl");
        String sig2 = signer.sign("monitor", "nl");
        assertNotEquals(sig1, sig2);
    }
}
