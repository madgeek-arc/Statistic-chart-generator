package gr.uoa.di.madgik.ChartDataFormatter.nl;

import java.util.List;

/** Stripped-down profile schema passed to NlSqlGenerator implementations. */
public class ProfileSchema {

    public record EntityDef(String name, String description, String sqlTable, List<String> baseConditions, List<FieldDef> fields, List<String> joinPaths) {}
    public record FieldDef(String name, String datatype, String description, String sqlTable, String column) {}

    private final String profileName;
    private final List<EntityDef> entities;

    public ProfileSchema(String profileName, List<EntityDef> entities) {
        this.profileName = profileName;
        this.entities = entities;
    }

    public String getProfileName() { return profileName; }
    public List<EntityDef> getEntities() { return entities; }
}
