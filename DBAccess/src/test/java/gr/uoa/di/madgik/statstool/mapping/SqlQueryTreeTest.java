package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Join;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SqlQueryTreeTest {

    private ProfileConfiguration buildProfile() {
        ProfileConfiguration pc = new ProfileConfiguration();
        // Logical entity names: result, project_results
        // SQL tables share same names in this fixture
        pc.tables.put("result", new Table("result", "id", null));
        pc.tables.put("project_results", new Table("project_results", "id", null));

        // Fields
        pc.fields.put("result.id", new Field("result", "id", "int"));
        pc.fields.put("project_results.value", new Field("project_results", "value", "int"));
        pc.fields.put("project_results.category", new Field("project_results", "category", "string"));
        // Additional tables for multi-hop test: result_organization and organization
        pc.tables.put("result_organization", new Table("result_organization", "id", null));
        pc.tables.put("organization", new Table("organization", "id", null));
        pc.fields.put("result_organization.result_id", new Field("result_organization", "result_id", "int"));
        pc.fields.put("result_organization.organization", new Field("result_organization", "organization", "int"));
        pc.fields.put("organization.id", new Field("organization", "id", "int"));
        pc.fields.put("organization.country", new Field("organization", "country", "string"));

        // Relations result -> project_results: result.id = project_results.result_id
        List<Join> rp = new ArrayList<>();
        rp.add(new Join("result", "id", "project_results", "result_id"));
        pc.relations.put("result.project_results", rp);
        pc.relations.put("project_results.result", Collections.singletonList(new Join("project_results", "result_id", "result", "id")));

        // Relations for multi-hop: result -> result_organization -> organization
        List<Join> rr = new ArrayList<>();
        rr.add(new Join("result", "id", "result_organization", "result_id"));
        pc.relations.put("result.result_organization", rr);
        pc.relations.put("result_organization.result", Collections.singletonList(new Join("result_organization", "result_id", "result", "id")));
        pc.relations.put("result_organization.organization", Collections.singletonList(new Join("result_organization", "organization", "organization", "id")));
        pc.relations.put("organization.result_organization", Collections.singletonList(new Join("organization", "id", "result_organization", "organization")));

        return pc;
    }

    @Test
    public void nonRootNonAggSelect_usesDirectJoin_groupByColumn() {
        ProfileConfiguration pc = buildProfile();
        // Build Query selecting a non-root non-aggregate field: result.project_results.value
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.project_results.value", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        // Non-agg non-root: must use a direct JOIN, not a derived subquery
        assertTrue(sql.contains(" FROM result r0 "), "Main query should start from root table");
        assertTrue(sql.contains("JOIN project_results"), "Non-root non-agg select must use a direct JOIN");
        assertFalse(sql.contains("LEFT JOIN ("), "No derived subquery expected for non-agg non-root select");
        // Column referenced directly (not via derived alias)
        assertTrue(sql.matches("(?s)SELECT\\s+\\w+\\.value\\s+FROM.*"), "Outer select should use direct column reference");
        // GROUP BY the non-root column
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.value.*"), "Non-agg non-root column must appear in GROUP BY");
        assertTrue(params.isEmpty(), "No bound parameters expected without filters");
    }

    @Test
    public void rootSelect_directAndGrouped_orderByXaxis() {
        ProfileConfiguration pc = buildProfile();
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.id", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, "xaxis");

        assertTrue(sql.startsWith("SELECT r0.id FROM result r0 "), "Root select should use direct column");
        assertTrue(sql.contains(" GROUP BY r0.id "), "Should group by root select when non-aggregated");
        assertTrue(sql.contains(" ORDER BY r0.id"), "Default order by xaxis should order by grouped expression");
        assertTrue(sql.endsWith(";"), "SQL should end with semicolon");
        assertEquals(0, params.size(), "No parameters expected");
    }

    @Test
    public void filterOnRelatedTable_buildsExistsSubquery_andBindsParams() {
        ProfileConfiguration pc = buildProfile();
        // Build filter on related table field
        Filter f = new Filter("result.project_results.value", "=", Collections.singletonList("5"), "int");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        // EXISTS with correlation r0.id = s0.result_id and predicate on s0.value
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM project_results s0 WHERE r0.id=s0.result_id AND s0.value=?)"),
                "Filter must be translated to EXISTS correlated subquery with bound parameter");
        assertEquals(1, params.size(), "One bound parameter expected");
        assertEquals(5, params.get(0), "Parameter should be integer 5");
    }

    @Test
    public void rootAggregate_plusNonRootNonAgg_groupsByDirectColumn() {
        ProfileConfiguration pc = buildProfile();

        // SUM on root id and plain non-root field (group-by key)
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),
                        new Select("result.project_results.category", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL rootAggregate_plusNonRootNonAgg_groupsByDirectColumn: \n" + sql);

        // Non-agg non-root must use a direct JOIN, not a derived subquery
        assertFalse(sql.contains("LEFT JOIN ("), "No derived subquery expected for non-agg non-root select");
        assertTrue(sql.matches("(?s).*JOIN\\s+project_results\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.result_id.*"),
                "Non-root non-agg select must use a direct JOIN on result_id");
        // Outer SELECT: SUM(root) and direct column reference
        assertTrue(sql.matches("(?is)SELECT\\s+SUM\\(r0\\.id\\)\\s*,\\s*\\w+\\.category\\s+FROM.*"),
                "Outer SELECT should be SUM(root) and the direct non-root column");
        // GROUP BY the direct column
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.category.*"),
                "Non-agg non-root column must appear directly in GROUP BY");
        assertTrue(sql.matches("(?s).*ORDER\\s+BY\\s+\\w+\\.category.*"),
                "ORDER BY should follow grouped column by default");
        assertTrue(params.isEmpty(), "No bound parameters expected");
    }

    @Test
    public void rootAggregate_plusNonRootAgg_onlyAggregatesDerived_whenNoGroup() {
        ProfileConfiguration pc = buildProfile();
        // root aggregate + non-root aggregate; since there is no non-agg select, outer query need not GROUP BY
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),
                        new Select("result.project_results.value", "sum", 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, "xaxis");
        System.out.println("[DEBUG_LOG] SQL rootAggregate_plusNonRootAgg_onlyAggregatesDerived_whenNoGroup: \n" + sql);

        // Should have derived join for child aggregate
        assertTrue(sql.contains("LEFT JOIN ("), "Expect a derived subquery for child aggregate");
        // Since there is no GROUP BY in outer (no non-agg projections), derived col can be selected plainly
        assertTrue(sql.matches("(?is)SELECT\\s+SUM\\(r0\\.id\\)\\s*,\\s*\\w+\\.c2\\s+FROM.*"),
                "Derived aggregate column should appear plainly when no GROUP BY is required");
        // No GROUP BY clause expected in this scenario at the OUTER level (subquery may have one)
        int outerStart = sql.indexOf(" FROM result r0 ");
        String afterFrom = outerStart >= 0 ? sql.substring(outerStart) : sql;
        // Look for GROUP BY only after the main FROM to avoid matching the subquery's GROUP BY
        assertFalse(afterFrom.matches("(?is).*\\)\\s*\\w+\\s+ON[^;]*GROUP\\s+BY[^;]*;.*"),
                "No outer GROUP BY expected when all projections are aggregates");
    }

    @Test
    public void rootAggregate_withNonRootNonAgg_andNonRootAgg_directJoinForNonAgg_derivedForAgg() {
        ProfileConfiguration pc = buildProfile();

        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),             // root aggregate
                        new Select("result.project_results.category", null, 2), // non-root non-agg (group key)
                        new Select("result.project_results.value", "sum", 3)   // non-root aggregate
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL rootAggregate_withNonRootNonAgg_andNonRootAgg_directJoinForNonAgg_derivedForAgg: \n" + sql);

        // Non-agg non-root (category) uses a direct JOIN
        assertTrue(sql.matches("(?s).*JOIN\\s+project_results\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.result_id.*"),
                "Non-agg non-root must use a direct JOIN");
        // Agg non-root (sum value) uses a derived LEFT JOIN subquery
        assertTrue(sql.contains("LEFT JOIN ("), "Agg non-root must still use a derived subquery");
        // GROUP BY the direct column
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.category(\\s|,|;).*"),
                "GROUP BY must use the direct non-agg column");
        // Outer SELECT: SUM(root), direct column, SUM(derived agg col)
        assertTrue(sql.matches("(?is)SELECT\\s+SUM\\(r0\\.id\\)\\s*,\\s*\\w+\\.category\\s*,\\s*SUM\\(\\w+\\.c3\\)\\s+FROM.*"),
                "Outer SELECT: SUM(root), direct non-agg col, SUM(derived agg col)");
        assertTrue(params.isEmpty(), "No bound parameters expected");
    }

    @Test
    public void multiHop_nonRootNonAgg_withRootAggregate_usesDirectJoins() {
        ProfileConfiguration pc = buildProfile();
        // Path: result -> result_organization -> organization.country
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),
                        new Select("result.result_organization.organization.country", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL multiHop_nonRootNonAgg_withRootAggregate_usesDirectJoins: \n" + sql);

        // Non-agg non-root multi-hop must use direct JOINs, not a derived subquery
        assertFalse(sql.contains("LEFT JOIN ("), "No derived subquery expected for non-agg non-root select");
        assertTrue(sql.matches("(?s).*JOIN\\s+result_organization\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.result_id.*"),
                "Must JOIN result_organization on result_id");
        assertTrue(sql.matches("(?s).*JOIN\\s+organization\\s+\\w+\\s+ON\\s+\\w+\\.organization=\\w+\\.id.*"),
                "Must JOIN organization on organization/id");
        // No reserved-word aliases ('to', 'or', etc.) from the join chain
        assertFalse(sql.matches("(?is).*\\bto\\b\\..*"), "No alias named 'to' should be used");
        // GROUP BY and ORDER BY on the direct country column
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.country.*"), "GROUP BY must use country column");
        assertTrue(sql.matches("(?s).*ORDER\\s+BY\\s+\\w+\\.country.*"), "ORDER BY must use country column");
        assertTrue(params.isEmpty(), "No bound parameters expected");
    }

    // ── Bug-fix coverage ──────────────────────────────────────────────────────

    @Test
    public void orFilterGroup_rootLevel_generatesSimpleOrPredicate_notExists() {
        // Regression: OR group whose fields are all on the root table must emit
        // (col = ? OR col = ?) — not an EXISTS+UNION ALL that references t0.id.
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f1 = new Filter("result.type", "=", Collections.singletonList("Software"), "text");
        Filter f2 = new Filter("result.type", "=", Collections.singletonList("Dataset"),  "text");
        FilterGroup fg = new FilterGroup(Arrays.asList(f1, f2), "OR");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("EXISTS"),       "Root-level OR must not generate EXISTS");
        assertFalse(sql.contains("UNION ALL"),    "Root-level OR must not generate UNION ALL");
        assertTrue(sql.contains("r0.type=?"),     "Both OR predicates must reference root alias");
        assertTrue(sql.matches("(?s).*\\(r0\\.type=\\?.*OR.*r0\\.type=\\?\\).*"),
                "Predicates must be wrapped in parentheses and joined with OR");
        assertEquals(2, params.size(), "Two parameters must be bound");
        assertEquals("Software", params.get(0));
        assertEquals("Dataset",  params.get(1));
    }

    @Test
    public void orFilterGroup_singleHop_generatesDirect_correlatedExists() {
        // Single hop-based filter in an OR group must emit a direct correlated EXISTS,
        // not a derived-table wrapper (which would be: EXISTS (SELECT 1 FROM (SELECT rid ...) u WHERE u.rid=...)).
        ProfileConfiguration pc = buildProfile();

        Filter f = new Filter("result.project_results.value", "=", Collections.singletonList("5"), "int");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "OR");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("EXISTS"),       "Must generate EXISTS");
        assertFalse(sql.contains("UNION ALL"),   "Single-branch OR must not generate UNION ALL");
        assertFalse(sql.contains("AS rid"),      "Single-branch OR must not generate derived table with rid alias");
        assertTrue(sql.matches("(?s).*EXISTS\\s*\\(SELECT 1 FROM project_results s0 WHERE r0\\.id=s0\\.result_id AND s0\\.value=\\?\\).*"),
                "Must generate direct correlated EXISTS with correlation and predicate in WHERE");
        assertEquals(1, params.size());
        assertEquals(5, params.get(0));
    }

    @Test
    public void orFilterGroup_withHops_generatesExistsWithUnionAll() {
        // Multiple hop-based filters in an OR group must use EXISTS+UNION ALL.
        ProfileConfiguration pc = buildProfile();

        Filter f1 = new Filter("result.project_results.value", "=", Collections.singletonList("5"),  "int");
        Filter f2 = new Filter("result.project_results.value", "=", Collections.singletonList("10"), "int");
        FilterGroup fg = new FilterGroup(Arrays.asList(f1, f2), "OR");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("EXISTS"),    "Hop-based OR must still use EXISTS");
        assertTrue(sql.contains("UNION ALL"), "Multiple-branch OR must use UNION ALL");
        assertEquals(2, params.size());
        assertEquals(5,  params.get(0));
        assertEquals(10, params.get(1));
    }

    @Test
    public void equalFilter_multipleValues_generatesInClause() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "=", Arrays.asList("Software", "Dataset", "Other"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type IN (?, ?, ?)"), "Multiple values for '=' must produce IN clause");
        assertEquals(3, params.size());
        assertEquals("Software", params.get(0));
        assertEquals("Dataset",  params.get(1));
        assertEquals("Other",    params.get(2));
    }

    @Test
    public void notEqualFilter_multipleValues_generatesNotInClause() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "!=", Arrays.asList("Software", "Dataset"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type NOT IN (?, ?)"), "Multiple values for '!=' must produce NOT IN clause");
        assertEquals(2, params.size());
        assertEquals("Software", params.get(0));
        assertEquals("Dataset",  params.get(1));
    }

    @Test
    public void orderByYaxis_usesAggregateExpression_notPosition() {
        // When orderBy != "xaxis", ORDER BY must use the aggregate expression, not '1'.
        ProfileConfiguration pc = buildProfile();

        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),
                        new Select("result.project_results.category", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, "yaxis");

        assertFalse(sql.toUpperCase().contains("ORDER BY 1"),   "Must not use positional ORDER BY");
        assertTrue(sql.matches("(?is).*ORDER\\s+BY\\s+sum\\(r0\\.id\\)\\s+DESC.*"),
                "Must ORDER BY the aggregate expression descending");
    }

    // ── Filter type coverage ──────────────────────────────────────────────────

    @Test
    public void betweenFilter_generatesBetweenPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.year", new Field("result", "year", "int"));

        Filter f = new Filter("result.year", "between", Arrays.asList("2019", "2023"), "int");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.year BETWEEN ? AND ?"), "Must generate BETWEEN predicate");
        assertEquals(2, params.size());
        assertEquals(2019, params.get(0));
        assertEquals(2023, params.get(1));
    }

    @Test
    public void containsFilter_generatesLikePredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "contains", Collections.singletonList("Open"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("lower(r0.type) LIKE CONCAT('%', ?, '%')"), "Must generate LIKE predicate for contains");
        assertEquals(1, params.size());
        assertEquals("open", params.get(0), "Value must be lowercased");
    }

    @Test
    public void startsWithFilter_generatesLikePredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "starts_with", Collections.singletonList("Open"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("lower(r0.type) LIKE CONCAT(?, '%')"), "Must generate prefix LIKE predicate");
        assertEquals("open", params.get(0));
    }

    @Test
    public void endsWithFilter_generatesLikePredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "ends_with", Collections.singletonList("Access"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("lower(r0.type) LIKE CONCAT('%', ?)"), "Must generate suffix LIKE predicate");
        assertEquals("access", params.get(0));
    }

    @Test
    public void isNullFilter_generatesIsNullPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "is_null", Collections.emptyList(), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type IS NULL"), "Must generate IS NULL predicate");
        assertEquals(0, params.size(), "is_null must bind no parameters");
    }

    @Test
    public void isNotNullFilter_generatesIsNotNullPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "is_not_null", Collections.emptyList(), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type IS NOT NULL"), "Must generate IS NOT NULL predicate");
        assertEquals(0, params.size(), "is_not_null must bind no parameters");
    }

    @Test
    public void inFilter_multipleValues_generatesInPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "in", Arrays.asList("a", "b", "c"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type IN (?, ?, ?)"), "Must generate IN predicate");
        assertEquals(Arrays.asList("a", "b", "c"), params, "Values must be bound in order");
    }

    @Test
    public void inFilter_singleValue_generatesInPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "in", Collections.singletonList("a"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type IN (?)"), "Single value must still generate IN, not =");
        assertEquals(1, params.size());
    }

    @Test
    public void notInFilter_generatesNotInPredicate() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "not_in", Arrays.asList("a", "b"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("r0.type NOT IN (?, ?)"), "Must generate NOT IN predicate");
        assertEquals(2, params.size());
    }

    @Test
    public void inFilter_emptyValues_throws() {
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        Filter f = new Filter("result.type", "in", Collections.emptyList(), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        assertThrows(IllegalArgumentException.class,
                () -> new SqlQueryBuilder(apiQuery, pc).getSqlQuery(new ArrayList<>(), null),
                "in with no values must be rejected");
    }

    @Test
    public void entityFilter_notDuplicated_acrossMultipleFieldPaths() {
        // Regression: addEntityFilters() was called once per select/filter field that traverses the
        // root entity, causing the entity table-filter (e.g. type='publication') to appear N times.
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));

        // Give the root entity a table-level filter (e.g. type='publication')
        Filter entityFilter = new Filter("type", "=", Collections.singletonList("publication"), "text");
        pc.tables.put("result", new Table("result", "id", Collections.singletonList(entityFilter)));

        // Two selects + two filter groups referencing the root entity — entity filter must appear once
        FilterGroup fg = new FilterGroup(Collections.singletonList(
                new Filter("result.type", "=", Collections.singletonList("Software"), "text")), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(
                        new Select("result.id", "count", 1),
                        new Select("result.project_results.category", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        // Count occurrences of the entity filter predicate in the WHERE clause
        int occurrences = 0;
        int idx = 0;
        while ((idx = sql.indexOf("r0.type=?", idx)) != -1) { occurrences++; idx++; }
        assertEquals(2, occurrences,
                "Entity filter 'type' must appear exactly twice: once as entity filter, once as explicit filter");
    }

    @Test
    public void multipleFilterGroups_joinedWithAnd() {
        // Two AND filter groups must be joined by AND between them in the WHERE clause.
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("result.type", new Field("result", "type", "text"));
        pc.fields.put("result.year", new Field("result", "year", "int"));

        FilterGroup g1 = new FilterGroup(Collections.singletonList(
                new Filter("result.type", "=", Collections.singletonList("Software"), "text")), "AND");
        FilterGroup g2 = new FilterGroup(Collections.singletonList(
                new Filter("result.year", ">", Collections.singletonList("2020"), "int")), "AND");

        Query apiQuery = new Query(null, null, Arrays.asList(g1, g2),
                Arrays.asList(new Select("result.id", "sum", 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.matches("(?s).*r0\\.type=\\?.*AND.*r0\\.year>\\?.*"),
                "Two filter groups must be joined with AND");
        assertEquals(2, params.size());
        assertEquals("Software", params.get(0));
        assertEquals(2020, params.get(1));
    }

    @Test
    public void countDistinct_withNonRootNonAggGroupBy_andExistsFilter_matchesOldBehavior() {
        ProfileConfiguration pc = buildProfile();
        // Additional root fields used as direct filters in this test
        pc.fields.put("result.type", new Field("result", "type", "text"));
        pc.fields.put("result.year", new Field("result", "year", "int"));
        // This is the exact pattern from the regression: COUNT DISTINCT results per country,
        // filtered by type, year (root), and country != X (non-root → EXISTS).
        // Old SQL: SELECT count(DISTINCT r0.id), o2.country FROM result r0
        //   JOIN result_organization r1 ON r0.id=r1.id JOIN organization o2 ON r1.organization=o2.id
        //   WHERE o2.country!=? AND r0.year>? AND r0.type=? GROUP BY o2.country ORDER BY 1 DESC LIMIT 30
        Filter typeFilter  = new Filter("result.type",  "=",  Collections.singletonList("Software"), "text");
        Filter yearFilter  = new Filter("result.year",  ">",  Collections.singletonList("2019"),     "int");
        Filter countryFilter = new Filter(
                "result.result_organization.organization.country", "!=",
                Collections.singletonList("Unknown"), "text");
        FilterGroup rootFilters    = new FilterGroup(Arrays.asList(typeFilter, yearFilter), "AND");
        FilterGroup countryFilters = new FilterGroup(Collections.singletonList(countryFilter), "AND");

        Query apiQuery = new Query(null, null,
                Arrays.asList(rootFilters, countryFilters),
                Arrays.asList(
                        new Select("result.id", "count", 1),
                        new Select("result.result_organization.organization.country", null, 2)
                ),
                "result", "test", 30, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL countDistinct_withNonRootNonAggGroupBy_andExistsFilter: \n" + sql);

        // Root aggregate is COUNT DISTINCT
        assertTrue(sql.matches("(?is)SELECT\\s+COUNT\\(DISTINCT\\s+r0\\.id\\).*"), "Must COUNT DISTINCT r0.id");
        // country is a direct column reference from the JOIN, not a derived alias
        assertTrue(sql.matches("(?is)SELECT.*\\w+\\.country.*FROM.*"), "country must be selected directly");
        // Direct JOINs for the non-agg GROUP BY path
        assertTrue(sql.matches("(?s).*JOIN\\s+result_organization\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.result_id.*"),
                "Must JOIN result_organization");
        assertTrue(sql.matches("(?s).*JOIN\\s+organization\\s+\\w+\\s+ON\\s+\\w+\\.organization=\\w+\\.id.*"),
                "Must JOIN organization");
        // Non-root filter on directly-joined table must be inline, NOT EXISTS
        assertFalse(sql.toUpperCase().contains("EXISTS"),
                "Filter on directly-joined GROUP BY field must not use EXISTS; got: " + sql);
        assertTrue(sql.matches("(?s).*WHERE.*\\.country!=\\?.*"),
                "country filter must appear as alias.country!=? in WHERE; got: " + sql);
        // Root-level filters are direct predicates
        assertTrue(sql.contains("r0.type=?"), "type filter must be a direct predicate");
        assertTrue(sql.contains("r0.year>?"), "year filter must be a direct predicate");
        // GROUP BY country directly
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.country.*"), "GROUP BY on country column");
        // LIMIT
        assertTrue(sql.contains("LIMIT 30"), "LIMIT must be present");
        // Parameters: type, year, country (all inline)
        assertEquals(3, params.size(), "Three bound parameters expected");
    }

    @Test
    public void andFilter_onDirectJoinField_appliesInlineNotExists() {
        // Regression: when a filter targets the same field that is also a GROUP BY SELECT dimension
        // (i.e. a single-hop non-aggregate field), the predicate must be applied directly on the
        // JOIN alias (e.g. d1.type != ?) rather than as EXISTS(...type != ?).
        // EXISTS semantics are wrong here: EXISTS(type != 'Other') is TRUE if ANY datasource row
        // is not 'Other', which still allows 'Other' rows through the GROUP BY.
        ProfileConfiguration pc = buildProfile();
        pc.tables.put("datasource", new Table("datasource", "id", null));
        pc.fields.put("datasource.type", new Field("datasource", "type", "text"));
        pc.relations.put("result.datasource", Collections.singletonList(
                new Join("result", "id", "datasource", "result_id")));

        Filter typeFilter = new Filter("result.datasource.type", "!=",
                Collections.singletonList("Other"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(typeFilter), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(
                        new Select("result.id", "count", 1),
                        new Select("result.datasource.type", null, 2)
                ),
                "result", "test", 20, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL andFilter_onDirectJoinField_appliesInlineNotExists: \n" + sql);

        // Filter must NOT use EXISTS
        assertFalse(sql.toUpperCase().contains("EXISTS"),
                "Filter on a directly-joined GROUP BY field must not use EXISTS; got: " + sql);
        // Filter must be a direct predicate on the join alias
        assertTrue(sql.matches("(?s).*WHERE.*\\.type!=\\?.*"),
                "Filter must appear as alias.type!=? in WHERE; got: " + sql);
        assertEquals(1, params.size(), "One bound parameter expected");
        assertEquals("Other", params.get(0));
    }

    @Test
    public void andFilter_throughDirectJoinToNonJoinedTable_usesDirectAliasForExistsCorrelation() {
        // Regression: result.datasource.organization.country = 'GR'
        // datasource is directly joined (it's also a GROUP BY SELECT field),
        // organization is NOT directly joined.
        // Expected: EXISTS (SELECT 1 FROM organization s0 WHERE d1.<fk>=s0.<pk> AND s0.country=?)
        // NOT:      EXISTS (SELECT 1 FROM datasource s0 JOIN organization s1 ... WHERE r0.id=s0.result_id ...)
        ProfileConfiguration pc = buildProfile();
        pc.tables.put("datasource", new Table("datasource", "id", null));
        pc.tables.put("organization", new Table("organization", "id", null));
        pc.fields.put("datasource.type", new Field("datasource", "type", "text"));
        pc.fields.put("organization.country", new Field("organization", "country", "text"));
        pc.relations.put("result.datasource", Collections.singletonList(
                new Join("result", "id", "datasource", "result_id")));
        pc.relations.put("datasource.organization", Collections.singletonList(
                new Join("datasource", "id", "organization", "datasource_id")));

        Filter countryFilter = new Filter("result.datasource.organization.country", "=",
                Collections.singletonList("GR"), "text");
        FilterGroup fg = new FilterGroup(Collections.singletonList(countryFilter), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(
                        new Select("result.id", "count", 1),
                        new Select("result.datasource.type", null, 2)
                ),
                "result", "test", 50, "yaxis", false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, "yaxis");
        System.out.println("[DEBUG_LOG] SQL andFilter_throughDirectJoinToNonJoinedTable: \n" + sql);

        // EXISTS must be anchored to the direct-join alias (d1), not spawn a fresh datasource alias
        assertFalse(sql.matches("(?s).*EXISTS.*FROM datasource.*JOIN organization.*"),
                "EXISTS must not re-join datasource; got: " + sql);
        assertTrue(sql.matches("(?s).*EXISTS.*FROM organization\\s+s0.*WHERE.*\\.id=s0\\.datasource_id.*"),
                "EXISTS must start from organization and correlate via d1.id; got: " + sql);
        assertEquals(1, params.size());
        assertEquals("GR", params.get(0));
    }

    @Test
    public void orFilter_onDirectlyJoinedTable_appliesInlineOrNotExists() {
        // Regression: OR group whose predicates all target the same directly-joined GROUP BY table
        // must emit (alias.col=? OR alias.col=?) inline — not EXISTS+UNION ALL.
        // EXISTS on a directly-joined table still allows non-matching rows through the JOIN.
        // E.g.: datasource is directly joined as d1; OR filter on datasource.type must be
        // (d1.type=? OR d1.type=?) not EXISTS (... FROM datasource WHERE type=? UNION ALL ...).
        ProfileConfiguration pc = buildProfile();
        pc.tables.put("datasource", new Table("datasource", "id", null));
        pc.fields.put("datasource.type", new Field("datasource", "type", "text"));
        pc.fields.put("datasource.name", new Field("datasource", "name", "text"));
        pc.relations.put("result.datasource", Collections.singletonList(
                new Join("result", "id", "datasource", "result_id")));

        Filter f1 = new Filter("result.datasource.type", "=",
                Collections.singletonList("Institutional Repository"), "text");
        Filter f2 = new Filter("result.datasource.type", "=",
                Collections.singletonList("Institutional CRIS"), "text");
        FilterGroup fg = new FilterGroup(Arrays.asList(f1, f2), "OR");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(
                        new Select("result.id", "count", 1),
                        new Select("result.datasource.name", null, 2)
                ),
                "result", "test", 60, "yaxis", false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, "yaxis");
        System.out.println("[DEBUG_LOG] SQL orFilter_onDirectlyJoinedTable_appliesInlineOrNotExists: \n" + sql);

        // Must NOT use EXISTS or UNION ALL — datasource is directly joined
        assertFalse(sql.toUpperCase().contains("EXISTS"),
                "OR filter on directly-joined table must not use EXISTS; got: " + sql);
        assertFalse(sql.contains("UNION ALL"),
                "OR filter on directly-joined table must not use UNION ALL; got: " + sql);
        // Must emit inline OR predicates on the direct-join alias
        assertTrue(sql.matches("(?s).*\\(\\w+\\.type=\\? OR \\w+\\.type=\\?\\).*"),
                "OR filter must be (alias.type=? OR alias.type=?); got: " + sql);
        assertEquals(2, params.size(), "Two bound parameters expected");
        assertEquals("Institutional Repository", params.get(0));
        assertEquals("Institutional CRIS", params.get(1));
    }

    @Test
    public void entityFilter_notDuplicated_whenEntityNameDiffersFromTableName() {
        // Regression: when entity logical name ("publication") differs from SQL table name ("result"),
        // addEntityFilters was called with path=entityName once (size-1 branch) and path=tableName
        // once (size-2 branch), generating two different dedup keys and adding the filter twice.
        ProfileConfiguration pc = buildProfile();
        pc.fields.put("publication.id", new Field("result", "id", "int"));
        pc.fields.put("publication.year", new Field("result", "year", "int"));
        pc.fields.put("publication.bestlicence", new Field("result", "bestlicence", "text"));

        Filter entityFilter = new Filter("type", "=", Collections.singletonList("publication"), "text");
        pc.tables.put("publication", new Table("result", "id", Collections.singletonList(entityFilter)));

        FilterGroup fg = new FilterGroup(Collections.singletonList(
                new Filter("publication.year", ">", Collections.singletonList("2020"), "int")), "AND");

        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(
                        new Select("publication.id", "count", 1),
                        new Select("publication.bestlicence", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        // Entity filter must appear exactly once (not duplicated due to key mismatch)
        int occurrences = 0;
        int idx = 0;
        while ((idx = sql.indexOf("r0.type=?", idx)) != -1) { occurrences++; idx++; }
        assertEquals(1, occurrences,
                "Entity filter must appear exactly once even when entity name differs from table name; got: " + sql);
    }

    // --- Denormalized field (reverse hidden-join) tests ---
    // Scenario: indi_pub_gold was a separate table joined via result.id = indi_pub_gold.id.
    // After denormalization, is_gold_oa lives directly on result. The profile still exposes
    // result.indi_pub_gold.is_gold_oa but sqlTable="result" short-circuits the join.

    private ProfileConfiguration buildProfileWithDenormField() {
        ProfileConfiguration pc = buildProfile();
        pc.tables.put("indi_pub_gold", new Table("indi_pub_gold", "id", null));
        // Denormalized: declared on indi_pub_gold entity, physically on result
        pc.fields.put("indi_pub_gold.is_gold_oa", new Field("result", "is_gold_oa", "boolean"));
        // Relation still present for non-denormalized fields / backwards compat
        pc.relations.put("result.indi_pub_gold", Collections.singletonList(new Join("result", "id", "indi_pub_gold", "id")));
        pc.relations.put("indi_pub_gold.result", Collections.singletonList(new Join("indi_pub_gold", "id", "result", "id")));
        return pc;
    }

    @Test
    public void denormSelect_readsDirectlyFromAncestor_noJoinToIntermediateTable() {
        ProfileConfiguration pc = buildProfileWithDenormField();
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.indi_pub_gold.is_gold_oa", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denormalized field must not produce a join to indi_pub_gold; got: " + sql);
        assertTrue(sql.matches("(?s).*\\br0\\.is_gold_oa\\b.*"),
                "Denormalized field must be read directly from root table alias; got: " + sql);
    }

    @Test
    public void denormFilter_appliesInlineOnRootTable_noExistsToIntermediateTable() {
        ProfileConfiguration pc = buildProfileWithDenormField();
        Filter f = new Filter("result.indi_pub_gold.is_gold_oa", "=", Collections.singletonList("true"), "boolean");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");
        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("result.id", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denormalized filter must not reference indi_pub_gold; got: " + sql);
        assertTrue(sql.matches("(?s).*\\br0\\.is_gold_oa\\b.*"),
                "Denormalized filter must reference column directly on root table alias; got: " + sql);
    }

    @Test
    public void denormField_deeperPath_joinsAncestorChain_skipsIntermediateTable() {
        // project -> result -> indi_pub_gold.is_gold_oa (denorm)
        // Must join project->result but NOT result->indi_pub_gold.
        ProfileConfiguration pc = buildProfileWithDenormField();
        pc.tables.put("project", new Table("project", "id", null));
        pc.fields.put("project.name", new Field("project", "name", "string"));
        pc.relations.put("project.result", Collections.singletonList(new Join("project", "id", "result", "id")));
        pc.relations.put("result.project", Collections.singletonList(new Join("result", "id", "project", "id")));

        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("project.result.indi_pub_gold.is_gold_oa", null, 1)),
                "project", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denormalized field in deep path must not join indi_pub_gold; got: " + sql);
        assertTrue(sql.contains("result"),
                "Deep path must still traverse up to result; got: " + sql);
        assertTrue(sql.matches("(?s).*\\.is_gold_oa\\b.*"),
                "Column is_gold_oa must appear in output; got: " + sql);
    }

    @Test
    public void forwardHiddenJoin_unaffected_byDenormLogic() {
        // Existing forward hidden-join: field declared on entity A, sqlTable points to
        // a table B NOT yet traversed. Must still produce a join to B.
        ProfileConfiguration pc = buildProfile();
        // result.result_classifications is a forward hidden-join (B not traversed as ancestor)
        pc.tables.put("result_classifications", new Table("result_classifications", "id", null));
        pc.fields.put("result.classification", new Field("result_classifications", "type", "string"));
        pc.relations.put("result.result_classifications",
                Collections.singletonList(new Join("result", "id", "result_classifications", "id")));

        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.classification", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertTrue(sql.contains("result_classifications"),
                "Forward hidden-join must still produce join to result_classifications; got: " + sql);
    }

    @Test
    public void denormAggregate_resolvedAsRootExpression_noDerivedSubquery() {
        // COUNT(DISTINCT result.indi_pub_gold.is_gold_oa) — denorm short-circuit gives
        // path "result.is_gold_oa". SqlQueryTree must treat it as a root aggregate
        // (inline COUNT(DISTINCT r0.is_gold_oa)), NOT a derived LEFT JOIN subquery.
        ProfileConfiguration pc = buildProfileWithDenormField();
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.indi_pub_gold.is_gold_oa", "count", 1)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denorm aggregate must not join indi_pub_gold; got: " + sql);
        assertFalse(sql.contains("LEFT JOIN ("),
                "Denorm aggregate on root-resolved field must not produce a derived subquery; got: " + sql);
        assertTrue(sql.matches("(?s).*COUNT\\(DISTINCT r0\\.is_gold_oa\\).*"),
                "Aggregate must inline COUNT(DISTINCT) on root alias; got: " + sql);
    }

    @Test
    public void denormIntermediateEntityFilters_notApplied_whenShortCircuited() {
        // Documents intentional behavior: if the intermediate entity (indi_pub_gold) has
        // entity-level filters, they are silently dropped when the field short-circuits
        // to an ancestor table. The denormalization process is assumed to have baked in
        // any constraints the filter represented.
        ProfileConfiguration pc = buildProfile();
        List<gr.uoa.di.madgik.statstool.domain.Filter> entityFilters = Collections.singletonList(
                new gr.uoa.di.madgik.statstool.domain.Filter("active", "=", Collections.singletonList("true"), "boolean"));
        pc.tables.put("indi_pub_gold", new Table("indi_pub_gold", "id", entityFilters));
        pc.fields.put("indi_pub_gold.is_gold_oa", new Field("result", "is_gold_oa", "boolean"));
        pc.relations.put("result.indi_pub_gold", Collections.singletonList(new Join("result", "id", "indi_pub_gold", "id")));
        pc.relations.put("indi_pub_gold.result", Collections.singletonList(new Join("indi_pub_gold", "id", "result", "id")));

        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.indi_pub_gold.is_gold_oa", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denorm short-circuit must not join indi_pub_gold; got: " + sql);
        assertFalse(sql.contains("active"),
                "Entity filters on the skipped intermediate entity must not appear in SQL; got: " + sql);
    }

    @Test
    public void denormFilter_inDeeperPath_noJoinToIntermediateTable() {
        // Filter path: project.result.indi_pub_gold.is_gold_oa (denorm)
        // Must not produce EXISTS or JOIN to indi_pub_gold; filter lands on result alias.
        ProfileConfiguration pc = buildProfileWithDenormField();
        pc.tables.put("project", new Table("project", "id", null));
        pc.fields.put("project.name", new Field("project", "name", "string"));
        pc.relations.put("project.result", Collections.singletonList(new Join("project", "id", "result", "id")));
        pc.relations.put("result.project", Collections.singletonList(new Join("result", "id", "project", "id")));

        Filter f = new Filter("project.result.indi_pub_gold.is_gold_oa", "=",
                Collections.singletonList("true"), "boolean");
        FilterGroup fg = new FilterGroup(Collections.singletonList(f), "AND");
        Query apiQuery = new Query(null, null, Collections.singletonList(fg),
                Arrays.asList(new Select("project.name", null, 1)),
                "project", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        assertFalse(sql.contains("indi_pub_gold"),
                "Denorm filter in deep path must not reference indi_pub_gold; got: " + sql);
        assertTrue(sql.matches("(?s).*\\.is_gold_oa\\b.*"),
                "Column is_gold_oa must appear in filter output; got: " + sql);
    }
}
