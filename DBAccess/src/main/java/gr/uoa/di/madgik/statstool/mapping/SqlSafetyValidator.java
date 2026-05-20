package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlSafetyValidator {

    private static final Set<String> DDL_KEYWORDS = Set.of(
            "DROP", "ALTER", "CREATE", "INSERT", "UPDATE", "DELETE", "TRUNCATE",
            "MERGE", "REPLACE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
    );

    private static final Pattern TABLE_REF_PATTERN =
            Pattern.compile("(?:FROM|JOIN)\\s+([\\w]+)", Pattern.CASE_INSENSITIVE);

    public static void validate(String sql, ProfileConfiguration profile) {
        String trimmed = sql.strip();
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            throw new IllegalArgumentException("LLM-generated SQL must be a SELECT statement");
        }

        String upper = trimmed.toUpperCase(Locale.ROOT);
        for (String keyword : DDL_KEYWORDS) {
            // match keyword as a whole word
            if (upper.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("LLM-generated SQL contains forbidden keyword: " + keyword);
            }
        }

        // Collect all known tables: entity tables + join intermediary tables
        Set<String> knownTables = new java.util.HashSet<>();
        profile.tables.values().forEach(t -> knownTables.add(t.getTable().toLowerCase(Locale.ROOT)));
        profile.relations.values().forEach(joins -> joins.forEach(j -> {
            knownTables.add(j.getFirst_table().toLowerCase(Locale.ROOT));
            knownTables.add(j.getSecond_table().toLowerCase(Locale.ROOT));
        }));

        Matcher m = TABLE_REF_PATTERN.matcher(trimmed);
        while (m.find()) {
            String referenced = m.group(1).toLowerCase(Locale.ROOT);
            if (!knownTables.contains(referenced)) {
                throw new IllegalArgumentException(
                        "LLM-generated SQL references unknown table: " + referenced);
            }
        }
    }
}
