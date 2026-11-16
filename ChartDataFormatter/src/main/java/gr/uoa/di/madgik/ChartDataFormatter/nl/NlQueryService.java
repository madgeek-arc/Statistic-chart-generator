package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.repositories.NlCachedEntry;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NlQueryService {

    private final NlSqlGenerator sqlGenerator;
    private final NlSqlCache nlSqlCache;
    private final NlRequestSigner signer;
    private final StatsService statsService;
    private final Mapper mapper;
    private final ProfileSchemaBuilder schemaBuilder;

    public NlQueryService(NlSqlGenerator sqlGenerator,
                          NlSqlCache nlSqlCache,
                          NlRequestSigner signer,
                          StatsService statsService,
                          Mapper mapper,
                          ProfileSchemaBuilder schemaBuilder) {
        this.sqlGenerator = sqlGenerator;
        this.nlSqlCache = nlSqlCache;
        this.signer = signer;
        this.statsService = statsService;
        this.mapper = mapper;
        this.schemaBuilder = schemaBuilder;
    }

    public void verifySignature(String profile, String canonicalNl, String sig) {
        verifySignature(profile, canonicalNl, null, sig);
    }

    public void verifySignature(String profile, String canonicalNl, List<FilterGroup> filters, String sig) {
        String cf = NlRequestSigner.canonicalFilters(filters);
        if (!signer.verify(profile, canonicalNl, cf, sig)) {
            throw new SecurityException("Invalid NL query signature");
        }
    }

    public Result execute(String profile, String canonicalNl) throws StatsServiceException {
        return execute(profile, canonicalNl, null);
    }

    public NlCachedEntry info(String profile, String canonicalNl, List<FilterGroup> filters) {
        ProfileConfiguration config = mapper.getProfileConfiguration(profile);
        String fingerprint = NlSqlCache.fingerprint(config);
        String filtersKey = NlRequestSigner.canonicalFilters(filters);
        String cacheKey = filtersKey.isEmpty() ? canonicalNl : canonicalNl + "\0" + filtersKey;
        return nlSqlCache.get(profile, cacheKey, fingerprint);
    }

    public Result execute(String profile, String canonicalNl, List<FilterGroup> extraFilters)
            throws StatsServiceException {
        ProfileConfiguration config = mapper.getProfileConfiguration(profile);
        String fingerprint = NlSqlCache.fingerprint(config);

        String filtersKey = NlRequestSigner.canonicalFilters(extraFilters);
        String cacheKey = filtersKey.isEmpty() ? canonicalNl : canonicalNl + "\0" + filtersKey;

        NlCachedEntry cached = nlSqlCache.get(profile, cacheKey, fingerprint);
        if (cached != null) {
            return statsService.queryRaw(cached.qwp());
        }

        ProfileSchema schema = schemaBuilder.build(profile);
        NlSqlRunner.Verified verified = NlSqlRunner.generateAndRun(
                sqlGenerator, statsService, config, profile, canonicalNl, schema, extraFilters);

        // Cache only SQL that has actually run on the profile's backend.
        nlSqlCache.put(profile, cacheKey,
                new NlCachedEntry(verified.qwp(), verified.sql().getDescription()), fingerprint);
        return verified.result();
    }
}
