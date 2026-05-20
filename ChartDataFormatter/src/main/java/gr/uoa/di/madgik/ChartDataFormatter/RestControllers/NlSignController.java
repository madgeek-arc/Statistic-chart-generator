package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Trusted-backend endpoint: signs a (profile, canonicalNl, filters) bundle.
 * Protected by X-Sign-Key header matching nl.sign-key in config.
 *
 * Dashboard backends call this to obtain a signature when they need to attach
 * pre-determined filter conditions to an existing canonical NL query.
 */
@RestController
@RequestMapping("/nl")
public class NlSignController {

    private final NlRequestSigner signer;
    private final String signKey;

    public NlSignController(NlRequestSigner signer,
                            @Value("${nl.sign-key:}") String signKey) {
        this.signer = signer;
        this.signKey = signKey;
    }

    @PostMapping(path = "/sign",
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    public ResponseEntity<SignResponse> sign(
            @RequestHeader(value = "X-Sign-Key", required = false) String key,
            @RequestBody SignRequest request) {

        if (signKey.isBlank() || !signKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (request.profile() == null || request.canonicalNl() == null) {
            return ResponseEntity.badRequest().build();
        }

        String canonicalFilters = NlRequestSigner.canonicalFilters(request.filters());
        String sig = signer.sign(request.profile(), request.canonicalNl(), canonicalFilters);

        return ResponseEntity.ok(new SignResponse(sig, canonicalFilters));
    }

    public record SignRequest(String profile, String canonicalNl, List<FilterGroup> filters) {}

    public record SignResponse(String sig, String canonicalFilters) {}
}
