package gr.uoa.di.madgik.statstool.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Join;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;

public class SqlQueryBuilder {

    private final Query query;

    private final ProfileConfiguration profileConfiguration;

    private final List<Select> mappedSelects = new ArrayList<>();
    private final List<Filter> mappedFilters = new ArrayList<>();

    public SqlQueryBuilder(Query query, ProfileConfiguration profileConfiguration) {
        this.query = query;
        this.profileConfiguration = profileConfiguration;
    }

    String getSqlQuery(List<Object> parameters) {
        return new SqlQueryTree(mapIntermediate()).makeQuery(parameters);
    }

    private Query mapIntermediate() {
        if (query.getSelect() == null) {
            return null;
        }

        int selectCount = 1;
        for (Select select : query.getSelect()) {
            String path = mapField(select.getField());
            mappedSelects.add(new Select(path, select.getAggregate(), selectCount));
            selectCount++;
        }

        if (query.getFilters() != null) {
            for (Filter filter : query.getFilters()) {
                String path = mapField(filter.getField());
                mappedFilters.add(new Filter(path, filter.getType(), filter.getValues(), getDataType(filter.getField())));
            }
        }

        Table entityTable = profileConfiguration.tables.get(query.getEntity());
        return new Query(mappedFilters, mappedSelects, entityTable.getTable(), query.getProfile());
    }

    private String getDataType(String field) {
        List<String> fldPath = new ArrayList<>(Arrays.asList(field.split("\\.")));
        return profileConfiguration.fields.get(fldPath.get(fldPath.size() - 2) + "." + fldPath.get(fldPath.size() - 1)).getDatatype();
    }

    private void addEntityFilters(String entity, String path) {
        List<Filter> filters = profileConfiguration.tables.get(entity).getFilters();
        if (filters != null) {
            for (Filter filter : filters) {
                mappedFilters.add(new Filter(path + "." + filter.getField(), filter.getType(), filter.getValues(), filter.getDatatype()));
            }
        }
    }

    private String mapField(String field) {
        String path = "";
        List<String> fldPath = new ArrayList<>(Arrays.asList(field.split("\\.")));
        if (fldPath.size() == 1) {
            path = profileConfiguration.tables.get(fldPath.get(0)).getTable() + "." + profileConfiguration.tables.get(fldPath.get(0)).getKey();
            addEntityFilters(fldPath.get(0), fldPath.get(0));
        } else {
            for (int i = 0; i < fldPath.size() - 2; i++) {
                Table table1 = profileConfiguration.tables.get(fldPath.get(i));
                if (i == 0) {
                    path += table1.getTable();
                }
                addEntityFilters(fldPath.get(i), path);
                Table table2 = profileConfiguration.tables.get(fldPath.get(i + 1));
                path += joinTables(table1.getTable(), table2.getTable());
            }
            Table table1 = profileConfiguration.tables.get(fldPath.get(fldPath.size() - 2));
            if (fldPath.size() - 2 == 0) {
                path += table1.getTable();
            }
            addEntityFilters(fldPath.get(fldPath.size() - 2), path);
            Field field1 = profileConfiguration.fields.get(fldPath.get(fldPath.size() - 2) + "." + fldPath.get(fldPath.size() - 1));
            if (field1 != null) {
                if (field1.getTable() != null) {
                    path += joinTables(table1.getTable(), field1.getTable());
                }
                path += "." + field1.getColumn();
            } else {
                Table table2 = profileConfiguration.tables.get(fldPath.get(fldPath.size() - 1));
                path += joinTables(table1.getTable(), table2.getTable());
                addEntityFilters(fldPath.get(fldPath.size() - 1), path);
                path += "." + table2.getKey();
            }
        }
        return path;
    }

    private String joinTables(String table1, String table2) {
        String path = "";
        List<Join> joins = profileConfiguration.relations.get(table1 + "." + table2);
        if (joins != null) {
            for (Join join : joins) {
                path += "(" + join.getFirst_field() + ")";
                path += ".(" + join.getSecond_field() + ")";
                path += join.getSecond_table();
            }
        }
        return path;
    }
}
