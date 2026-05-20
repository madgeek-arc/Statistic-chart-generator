package gr.uoa.di.madgik.statstool.mapping;

import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SqlSafetyValidatorTest {

    private ProfileConfiguration profile;

    @BeforeEach
    void setup() {
        profile = new ProfileConfiguration();
        profile.tables.put("result", new Table("result", "id", null));
        profile.tables.put("datasource", new Table("datasource", "id", null));
    }

    @Test
    void validSelect_passes() {
        assertDoesNotThrow(() ->
                SqlSafetyValidator.validate("SELECT COUNT(*) FROM result", profile));
    }

    @Test
    void selectWithJoin_passes() {
        assertDoesNotThrow(() ->
                SqlSafetyValidator.validate(
                        "SELECT r.id, d.type FROM result r JOIN datasource d ON r.id=d.result_id",
                        profile));
    }

    @Test
    void selectWithWhereAndGroupBy_passes() {
        assertDoesNotThrow(() ->
                SqlSafetyValidator.validate(
                        "SELECT COUNT(DISTINCT id), year FROM result WHERE type=? GROUP BY year ORDER BY year",
                        profile));
    }

    @Test
    void nonSelect_insert_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("INSERT INTO result VALUES (1)", profile));
        assertTrue(ex.getMessage().contains("SELECT"));
    }

    @Test
    void nonSelect_update_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("UPDATE result SET type=? WHERE id=?", profile));
    }

    @Test
    void nonSelect_delete_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("DELETE FROM result WHERE id=?", profile));
    }

    @Test
    void ddlKeyword_drop_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("SELECT * FROM result; DROP TABLE result", profile));
        assertTrue(ex.getMessage().contains("DROP"));
    }

    @Test
    void ddlKeyword_truncate_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("SELECT 1; TRUNCATE result", profile));
        assertTrue(ex.getMessage().contains("TRUNCATE"));
    }

    @Test
    void unknownTable_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("SELECT * FROM secret_table", profile));
        assertTrue(ex.getMessage().contains("secret_table"));
    }

    @Test
    void mixedCaseKeyword_stillCaught() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlSafetyValidator.validate("Select * From result; Drop Table result", profile));
    }

    @Test
    void selectWithAlias_knownTable_passes() {
        assertDoesNotThrow(() ->
                SqlSafetyValidator.validate("SELECT COUNT(*) FROM result r0 JOIN datasource d1 ON r0.id=d1.result_id", profile));
    }
}
