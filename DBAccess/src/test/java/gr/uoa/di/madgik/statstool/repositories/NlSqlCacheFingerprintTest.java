package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NlSqlCacheFingerprintTest {

    private static ProfileConfiguration config(String entityName, String sqlTable) {
        ProfileConfiguration c = new ProfileConfiguration();
        c.tables.put(entityName, new Table(sqlTable, "id", null));
        return c;
    }

    @Test
    void fingerprint_isStable() {
        ProfileConfiguration c = config("result", "result_table");
        c.fields.put("result.type", new Field("result_table", "type_col", null));
        assertEquals(NlSqlCache.fingerprint(c), NlSqlCache.fingerprint(c));
    }

    @Test
    void fingerprint_sameContents_returnsSameHash() {
        ProfileConfiguration c1 = config("result", "result_table");
        c1.fields.put("result.type", new Field("result_table", "type_col", null));

        ProfileConfiguration c2 = config("result", "result_table");
        c2.fields.put("result.type", new Field("result_table", "type_col", null));

        assertEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_differentSqlTableName_returnsDifferentHash() {
        ProfileConfiguration c1 = config("result", "result_v1");
        ProfileConfiguration c2 = config("result", "result_v2");
        assertNotEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_additionalTable_returnsDifferentHash() {
        ProfileConfiguration c1 = config("result", "result_table");

        ProfileConfiguration c2 = config("result", "result_table");
        c2.tables.put("publication", new Table("publication_table", "id", null));

        assertNotEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_additionalField_returnsDifferentHash() {
        ProfileConfiguration c1 = config("result", "result_table");

        ProfileConfiguration c2 = config("result", "result_table");
        c2.fields.put("result.type", new Field("result_table", "type_col", null));

        assertNotEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_differentFieldColumn_returnsDifferentHash() {
        ProfileConfiguration c1 = config("result", "result_table");
        c1.fields.put("result.type", new Field("result_table", "col_v1", null));

        ProfileConfiguration c2 = config("result", "result_table");
        c2.fields.put("result.type", new Field("result_table", "col_v2", null));

        assertNotEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_differentFieldSqlTable_returnsDifferentHash() {
        ProfileConfiguration c1 = config("result", "result_table");
        c1.fields.put("result.year", new Field("result_table", "year", null));

        ProfileConfiguration c2 = config("result", "result_table");
        c2.fields.put("result.year", new Field("other_table", "year", null));

        assertNotEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_tableInsertionOrderDoesNotMatter() {
        ProfileConfiguration c1 = new ProfileConfiguration();
        c1.tables.put("aaa", new Table("tbl_a", "id", null));
        c1.tables.put("zzz", new Table("tbl_z", "id", null));

        ProfileConfiguration c2 = new ProfileConfiguration();
        c2.tables.put("zzz", new Table("tbl_z", "id", null));
        c2.tables.put("aaa", new Table("tbl_a", "id", null));

        assertEquals(NlSqlCache.fingerprint(c1), NlSqlCache.fingerprint(c2));
    }

    @Test
    void fingerprint_emptyConfig_returnsNonEmptyString() {
        String fp = NlSqlCache.fingerprint(new ProfileConfiguration());
        assertNotNull(fp);
        assertFalse(fp.isEmpty());
    }
}
