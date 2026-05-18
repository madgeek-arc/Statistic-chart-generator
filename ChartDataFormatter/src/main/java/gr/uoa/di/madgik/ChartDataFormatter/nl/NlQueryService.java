package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.SqlSafetyValidator;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import java.util.ArrayList;
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
        if (!signer.verify(profile, canonicalNl, sig)) {
            throw new SecurityException("Invalid NL query signature");
        }
    }

    public Result execute(String profile, String canonicalNl) throws StatsServiceException {
        ProfileConfiguration config = mapper.getProfileConfiguration(profile);
        String fingerprint = NlSqlCache.fingerprint(config);

        QueryWithParameters cached = nlSqlCache.get(profile, canonicalNl, fingerprint);
        if (cached != null) {
            return statsService.queryRaw(cached);
        }

        ProfileSchema schema = schemaBuilder.build(profile);
        SqlResult sqlResult = sqlGenerator.generate(canonicalNl, profile, schema);

        SqlSafetyValidator.validate(sqlResult.getSql(), config);

        QueryWithParameters qwp = new QueryWithParameters(
                sqlResult.getSql(),
                new ArrayList<>(sqlResult.getParameters()),
                profile + ".public"
        );
        nlSqlCache.put(profile, canonicalNl, qwp, fingerprint);
        return statsService.queryRaw(qwp);
    }
}
