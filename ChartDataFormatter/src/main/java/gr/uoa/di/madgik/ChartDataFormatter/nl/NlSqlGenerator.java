package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.statstool.domain.FilterGroup;

import java.util.List;

public interface NlSqlGenerator {

    /**
     * Translate a canonical NL query to a SQL prepared statement.
     * extraFilters are pre-resolved DSL filters to incorporate; null/empty = no extra filters.
     */
    SqlResult generate(String canonicalNl, String profile, ProfileSchema schema,
                       List<FilterGroup> extraFilters);

    default SqlResult generate(String canonicalNl, String profile, ProfileSchema schema) {
        return generate(canonicalNl, profile, schema, List.of());
    }

    /**
     * Correct a previously generated statement that was rejected by validation or by the
     * database. Returns the corrected statement, or null when this generator cannot repair.
     */
    default SqlResult repair(String canonicalNl, String profile, ProfileSchema schema,
                             List<FilterGroup> extraFilters, SqlResult failed, String error) {
        return null;
    }
}
