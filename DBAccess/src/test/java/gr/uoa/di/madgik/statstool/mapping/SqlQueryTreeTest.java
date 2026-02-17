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
    public void nonRootSelect_usesDerivedLeftJoin_impalaCompatible() {
        ProfileConfiguration pc = buildProfile();
        // Build Query selecting a non-root field: result.project_results.value
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result.project_results.value", null, 1)),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);

        // Expect: main FROM root and a LEFT JOIN to a derived subquery that aggregates by result_id
        assertTrue(sql.contains(" FROM result r0 "), "Main query should start from root table");
        assertTrue(sql.matches("(?s).*LEFT\\s+JOIN\\s*\\(\\s*SELECT\\s+\\w+\\.result_id\\s+AS\\s+k.*FROM\\s+project_results\\s+\\w+\\s+GROUP\\s+BY\\s+\\w+\\.result_id\\s*\\)\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.k.*"),
                "Non-root select must be produced by a LEFT JOIN to a derived subquery grouped by child key");
        // Outer select should reference derived alias column c1 (column alias based on select order)
        assertTrue(sql.matches("(?s)SELECT\\s+\\w+\\.c1\\s+FROM.*"), "Outer select should project column from derived table");
        // No parameters expected
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
    public void rootAggregate_plusNonRootNonAgg_groupsByDerivedColumn() {
        ProfileConfiguration pc = buildProfile();

        // SUM on root id (just for testing) and plain non-root field
        Query apiQuery = new Query(null, null, new ArrayList<>(),
                Arrays.asList(
                        new Select("result.id", "sum", 1),
                        new Select("result.project_results.category", null, 2)
                ),
                "result", "test", 0, null, false);

        List<Object> params = new ArrayList<>();
        String sql = new SqlQueryBuilder(apiQuery, pc).getSqlQuery(params, null);
        System.out.println("[DEBUG_LOG] SQL rootAggregate_plusNonRootNonAgg_groupsByDerivedColumn: \n" + sql);

        // Expect derived join present
        assertTrue(sql.matches("(?s).*LEFT\\s+JOIN\\s*\\(.*FROM\\s+project_results\\s+\\w+\\s+GROUP\\s+BY\\s+\\w+\\.result_id.*\\)\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.k.*"),
                "Should join a derived subquery on child key");
        // Expect outer select contains SUM(r0.id) and plain derived column as second projection
        assertTrue(sql.matches("(?is)SELECT\\s+SUM\\(r0\\.id\\)\\s*,\\s*\\w+\\.c2\\s+FROM.*"),
                "Outer SELECT should be SUM(root) and plain derived column for non-agg");
        // GROUP BY must include derived non-agg column
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.c2.*"),
                "Derived non-aggregated column must be grouped when root has aggregates");
        // ORDER BY defaults to grouped expressions when orderBy is null
        assertTrue(sql.matches("(?s).*ORDER\\s+BY\\s+\\w+\\.c2.*"),
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
    public void rootAggregate_withNonRootNonAgg_andNonRootAgg_groupsOnlyNonAgg_andSumsAgg() {
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
        System.out.println("[DEBUG_LOG] SQL rootAggregate_withNonRootNonAgg_andNonRootAgg_groupsOnlyNonAgg_andSumsAgg: \n" + sql);

        // Must include GROUP BY derived non-agg col only
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.c2(\\s|,|$).*"),
                "GROUP BY must include only the non-aggregated derived column");
        // The aggregated child column must be wrapped with SUM at outer level because of grouping
        assertTrue(sql.matches("(?is)SELECT.*SUM\\(r0\\.id\\)\\s*,\\s*\\w+\\.c2\\s*,\\s*SUM\\(\\w+\\.c3\\).*FROM.*"),
                "Outer SELECT should wrap derived aggregated column with SUM when grouping");
        assertTrue(params.isEmpty(), "No bound parameters expected");
    }

    @Test
    public void multiHop_nonRootNonAgg_withRootAggregate_usesNumericAliases_notKeywords() {
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
        System.out.println("[DEBUG_LOG] SQL multiHop_nonRootNonAgg_withRootAggregate_usesNumericAliases_notKeywords: \n" + sql);

        // Should contain a derived LEFT JOIN with numeric t-aliases within the subquery (no reserved words) and group by child key
        assertTrue(sql.matches("(?s).*LEFT\\s+JOIN\\s*\\(\\s*SELECT\\s+t1\\.result_id\\s+AS\\s+k.*FROM\\s+result_organization\\s+t1\\s+JOIN\\s+organization\\s+t2.*GROUP\\s+BY\\s+t1\\.result_id\\s*\\)\\s+\\w+\\s+ON\\s+r0\\.id=\\w+\\.k.*"),
                "Derived subquery should use numeric aliases (t1/t2/...) and group by child key");
        // Ensure no reserved-word alias like 'to' appears
        assertFalse(sql.matches("(?is).*\\s+to\\s*\\."), "No alias named 'to' should be used");
        // GROUP BY must include derived col
        assertTrue(sql.matches("(?s).*GROUP\\s+BY\\s+\\w+\\.c2.*"),
                "Outer GROUP BY must include the derived non-agg column");
        assertTrue(params.isEmpty(), "No bound parameters expected");
    }
}
