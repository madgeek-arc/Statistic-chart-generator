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

        Matcher m = TABLE_REF_PATTERN.matcher(trimmed);
        while (m.find()) {
            String referenced = m.group(1).toLowerCase(Locale.ROOT);
            boolean found = profile.tables.values().stream()
                    .anyMatch(t -> t.getTable().toLowerCase(Locale.ROOT).equals(referenced));
            if (!found) {
                throw new IllegalArgumentException(
                        "LLM-generated SQL references unknown table: " + referenced);
            }
        }
    }
}
