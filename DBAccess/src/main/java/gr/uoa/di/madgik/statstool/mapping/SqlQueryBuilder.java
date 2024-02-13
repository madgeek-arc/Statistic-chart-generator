package gr.uoa.di.madgik.statstool.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
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
    private final List<FilterGroup> mappedFilters = new ArrayList<>();
    private final List<Filter> entityFilters = new ArrayList<>();

    public SqlQueryBuilder(Query query, ProfileConfiguration profileConfiguration) {
        this.query = query;
        this.profileConfiguration = profileConfiguration;
    }

    String getSqlQuery(List<Object> parameters, String orderBy) {
        return new SqlQueryTree(mapIntermediate()).makeQuery(parameters, orderBy);
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
            for (FilterGroup filterGroup : query.getFilters()) {
                List<Filter> filters = new ArrayList<>();
                for (Filter filter : filterGroup.getGroupFilters()) {
                    String path = mapField(filter.getField());
                    filters.add(new Filter(path, filter.getType(), filter.getValues(), getDataType(filter.getField())));
                }
                mappedFilters.add(new FilterGroup(filters, filterGroup.getOp().toUpperCase()));
            }
        }

        mappedFilters.add(new FilterGroup(entityFilters, "AND"));

        Table entityTable = profileConfiguration.tables.get(query.getEntity());
        return new Query(null, null, mappedFilters, mappedSelects, entityTable.getTable(), query.getProfile(), query.getLimit(), query.getOrderBy(), query.isUseCache());
    }

    private String getDataType(String field) {
        List<String> fldPath = new ArrayList<>(Arrays.asList(field.split("\\.")));
        return profileConfiguration.fields.get(fldPath.get(fldPath.size() - 2) + "." + fldPath.get(fldPath.size() - 1)).getDatatype();
    }

    private void addEntityFilters(String entity, String path) {
        List<Filter> filters = profileConfiguration.tables.get(entity).getFilters();
        if (filters != null) {
            for (Filter filter : filters) {
                entityFilters.add(new Filter(path + "." + filter.getField(), filter.getType(), filter.getValues(), filter.getDatatype()));
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
                if (field1.getTable() != null && !field1.getTable().equals(table1.getTable())) {
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
