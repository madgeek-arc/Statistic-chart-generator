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

        // Relations result -> project_results: result.id = project_results.result_id
        List<Join> rp = new ArrayList<>();
        rp.add(new Join("result", "id", "project_results", "result_id"));
        pc.relations.put("result.project_results", rp);
        pc.relations.put("project_results.result", Collections.singletonList(new Join("project_results", "result_id", "result", "id")));

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
}
