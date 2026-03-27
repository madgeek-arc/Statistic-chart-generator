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
        // Non-root filter becomes EXISTS, not another JOIN
        assertTrue(sql.matches("(?s).*EXISTS\\s*\\(SELECT 1 FROM result_organization.*JOIN organization.*country!=\\?.*\\).*"),
                "Non-root filter must use EXISTS subquery");
        // Root-level filters are direct predicates
        assertTrue(sql.contains("r0.type=?"), "type filter must be a direct predicate");
        assertTrue(sql.contains("r0.year>?"), "year filter must be a direct predicate");
        // GROUP BY country directly
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.country.*"), "GROUP BY on country column");
        // LIMIT
        assertTrue(sql.contains("LIMIT 30"), "LIMIT must be present");
        // Parameters: type, year, country (EXISTS param order)
        assertEquals(3, params.size(), "Three bound parameters expected");
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
}
