package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.ChartDataFormatter.nl.signing.NlRequestSigner;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.SqlSafetyValidator;
import gr.uoa.di.madgik.statstool.repositories.NlSqlCache;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class NlQueryService {

    private final NlSqlGenerator sqlGenerator;
    private final NlSqlCache nlSqlCache;
    private final NlRequestSigner signer;
    private final StatsService statsService;
    private final Mapper mapper;

    public NlQueryService(NlSqlGenerator sqlGenerator,
                          NlSqlCache nlSqlCache,
                          NlRequestSigner signer,
                          StatsService statsService,
                          Mapper mapper) {
        this.sqlGenerator = sqlGenerator;
        this.nlSqlCache = nlSqlCache;
        this.signer = signer;
        this.statsService = statsService;
        this.mapper = mapper;
    }

    public void verifySignature(String profile, String canonicalNl, String sig) {
        if (!signer.verify(profile, canonicalNl, sig)) {
            throw new SecurityException("Invalid NL query signature");
        }
    }

    public Result execute(String profile, String canonicalNl) throws StatsServiceException {
        QueryWithParameters cached = nlSqlCache.get(profile, canonicalNl);
        if (cached != null) {
            return statsService.queryRaw(cached);
        }

        ProfileSchema schema = buildSchema(profile);
        SqlResult sqlResult = sqlGenerator.generate(canonicalNl, profile, schema);

        SqlSafetyValidator.validate(sqlResult.getSql(), mapper.getProfileConfiguration(profile));

        QueryWithParameters qwp = new QueryWithParameters(
                sqlResult.getSql(),
                new ArrayList<>(sqlResult.getParameters()),
                profile + ".public"
        );
        nlSqlCache.put(profile, canonicalNl, qwp);
        return statsService.queryRaw(qwp);
    }

    private ProfileSchema buildSchema(String profile) {
        var entities = mapper.getEntities(profile);
        var defs = entities.values().stream().map(entity -> {
            var fields = entity.getFields().stream()
                    .map(f -> new ProfileSchema.FieldDef(f.getName(), f.getType(), f.getName()))
                    .collect(Collectors.toList());
            return new ProfileSchema.EntityDef(entity.getName(), entity.getName(), fields, entity.getRelations());
        }).collect(Collectors.toList());
        return new ProfileSchema(profile, defs);
    }
}
