package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.mapping.SqlSafetyValidator;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates SQL for a canonical NL query, validates it and runs it once. If validation or the
 * database rejects it, the generator gets one chance to repair the statement from the error.
 * Callers cache the returned statement only after this succeeded, so SQL that does not run
 * on the profile's backend never reaches the NL SQL cache.
 */
public final class NlSqlRunner {

    private static final Logger log = LogManager.getLogger(NlSqlRunner.class);

    /** Database errors can embed the whole statement and long driver chains; keep the prompt small. */
    private static final int MAX_ERROR_LENGTH = 2000;

    public record Verified(SqlResult sql, QueryWithParameters qwp, Result result) {}

    private NlSqlRunner() {}

    public static Verified generateAndRun(NlSqlGenerator generator, StatsService statsService,
                                          ProfileConfiguration config, String profile,
                                          String canonicalNl, ProfileSchema schema,
                                          List<FilterGroup> extraFilters) throws StatsServiceException {
        SqlResult sql = generator.generate(canonicalNl, profile, schema, extraFilters);
        try {
            return run(statsService, config, profile, sql);
        } catch (IllegalArgumentException | StatsServiceException e) {
            String error = truncate(e.getMessage());
            SqlResult repaired = generator.repair(canonicalNl, profile, schema, extraFilters, sql, error);
            if (repaired == null) {
                throw e;
            }
            log.warn("NL SQL for profile '" + profile + "' failed (" + error + "); retrying with repaired SQL: "
                    + repaired.getSql());
            return run(statsService, config, profile, repaired);
        }
    }

    private static Verified run(StatsService statsService, ProfileConfiguration config, String profile,
                                SqlResult sql) throws StatsServiceException {
        SqlSafetyValidator.validate(sql.getSql(), config);
        QueryWithParameters qwp = new QueryWithParameters(
                sql.getSql(), new ArrayList<>(sql.getParameters()), profile + ".public");
        Result result = statsService.queryRaw(qwp);
        return new Verified(sql, qwp, result);
    }

    private static String truncate(String message) {
        if (message == null) return "unknown error";
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH) + "...";
    }
}
