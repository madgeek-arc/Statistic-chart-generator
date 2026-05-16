package gr.uoa.di.madgik.ChartDataFormatter.nl;

public interface NlSqlGenerator {

    /**
     * Translate a canonical natural-language query to a SQL prepared statement.
     *
     * @param canonicalNl the finalised NL description agreed with the user
     * @param profile     profile name
     * @param schema      schema context built from the profile configuration
     * @return SQL string with {@code ?} placeholders and the matching parameter list
     */
    SqlResult generate(String canonicalNl, String profile, ProfileSchema schema);
}
