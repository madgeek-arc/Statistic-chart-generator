package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.ChartDataFormatter.nl.NlQueryService;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.repositories.NlCachedEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/nl")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST}, origins = "*")
public class NlInfoController {

    private final NlQueryService nlQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NlInfoController(NlQueryService nlQueryService) {
        this.nlQueryService = nlQueryService;
    }

    /**
     * Returns the SQL and description for a signed canonical NL query without executing it.
     * Clients use this to populate read-only query metadata when loading a chart URL.
     *
     * Query params:
     *   profile   — data profile name
     *   nl        — canonical NL query string
     *   sig       — HMAC signature (covers profile + nl + canonicalFilters)
     *   filters   — (optional) JSON-encoded List<FilterGroup>
     */
    @GetMapping(path = "/info", produces = "application/json; charset=UTF-8")
    public ResponseEntity<InfoResponse> info(
            @RequestParam String profile,
            @RequestParam String nl,
            @RequestParam String sig,
            @RequestParam(required = false) String filters) {

        List<FilterGroup> filterList = parseFilters(filters);

        try {
            nlQueryService.verifySignature(profile, nl, filterList, sig);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }

        NlCachedEntry entry = nlQueryService.info(profile, nl, filterList);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new InfoResponse(
                entry.qwp().getQuery(),
                entry.description()
        ));
    }

    private List<FilterGroup> parseFilters(String filters) {
        if (filters == null || filters.isBlank()) return List.of();
        try {
            return objectMapper.readValue(filters,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FilterGroup.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public record InfoResponse(String sql, String description) {}
}
