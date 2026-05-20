package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.domain.FilterGroup;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.mapping.domain.ProfileConfiguration;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProfileSchemaBuilder {

    private final Mapper mapper;

    public ProfileSchemaBuilder(Mapper mapper) {
        this.mapper = mapper;
    }

    public ProfileSchema build(String profile) {
        var entities = mapper.getEntities(profile);
        var config = mapper.getProfileConfiguration(profile);
        var defs = entities.values().stream().map(entity -> {
            var table = config.tables.get(entity.getName());
            String sqlTable = table != null ? table.getTable() : entity.getName();
            List<String> baseConditions = table != null && table.getFilters() != null
                    ? table.getFilters().stream().map(this::filterToCondition).collect(Collectors.toList())
                    : List.of();
            var fields = entity.getFields().stream().map(ef -> {
                var f = config.fields.get(entity.getName() + "." + ef.getName());
                String fSqlTable = f != null ? f.getTable() : sqlTable;
                String fColumn = f != null ? f.getColumn() : ef.getName();
                return new ProfileSchema.FieldDef(ef.getName(), ef.getType(), ef.getName(), fSqlTable, fColumn);
            }).collect(Collectors.toList());
            List<String> joinPaths = entity.getRelations().stream().map(rel -> {
                var joins = config.relations.get(entity.getName() + "." + rel);
                if (joins == null || joins.isEmpty()) return "join " + rel;
                StringBuilder jp = new StringBuilder("join " + rel + " via: ");
                for (int i = 0; i < joins.size(); i++) {
                    var j = joins.get(i);
                    if (i > 0) jp.append(" → ");
                    jp.append(j.getFirst_table()).append(".").append(j.getFirst_field())
                      .append(" = ").append(j.getSecond_table()).append(".").append(j.getSecond_field());
                }
                return jp.toString();
            }).collect(Collectors.toList());
            return new ProfileSchema.EntityDef(entity.getName(), entity.getName(), sqlTable, baseConditions, fields, joinPaths);
        }).collect(Collectors.toList());
        return new ProfileSchema(profile, defs);
    }

    /**
     * Pre-resolves DSL filter fields to their SQL form so the LLM receives unambiguous conditions.
     * Each ResolvedFilter carries the exact SQL condition, the join hint (if the field is in a
     * joined table), and the parameter value(s).
     */
    public record ResolvedFilter(String sqlCondition, String joinHint, List<String> params) {}

    public List<ResolvedFilter> resolveFilterFields(String profile, List<FilterGroup> filters) {
        if (filters == null || filters.isEmpty()) return List.of();
        ProfileConfiguration config = mapper.getProfileConfiguration(profile);
        List<ResolvedFilter> resolved = new ArrayList<>();
        for (FilterGroup group : filters) {
            if (group.getGroupFilters() == null) continue;
            for (Filter f : group.getGroupFilters()) {
                resolved.add(resolveOne(f, config));
            }
        }
        return resolved;
    }

    private ResolvedFilter resolveOne(Filter f, ProfileConfiguration config) {
        // field is "entity.fieldName" — look it up in config.fields
        Field fieldDef = config.fields.get(f.getField());
        String sqlTable, sqlColumn;
        if (fieldDef != null) {
            sqlTable = fieldDef.getTable();
            sqlColumn = fieldDef.getColumn();
        } else {
            // fall back: treat the field string as-is
            int dot = f.getField().lastIndexOf('.');
            sqlTable = dot > 0 ? f.getField().substring(0, dot) : "";
            sqlColumn = dot > 0 ? f.getField().substring(dot + 1) : f.getField();
        }

        String sqlRef = sqlTable.isEmpty() ? sqlColumn : sqlTable + "." + sqlColumn;

        // build SQL condition with ? placeholders
        String condition;
        if (f.getValues().size() == 1) {
            condition = sqlRef + " " + mapOperator(f.getType()) + " ?";
        } else {
            String placeholders = f.getValues().stream().map(v -> "?").collect(Collectors.joining(", "));
            condition = sqlRef + " IN (" + placeholders + ")";
        }

        // find join hint: look for a relation whose target table matches sqlTable
        String joinHint = findJoinHint(f.getField(), config);

        return new ResolvedFilter(condition, joinHint, f.getValues());
    }

    private String mapOperator(String type) {
        if (type == null) return "=";
        return switch (type) {
            case "eq", "=" -> "=";
            case "not_eq", "!=" -> "!=";
            case "gt", ">" -> ">";
            case "gte", ">=" -> ">=";
            case "lt", "<" -> "<";
            case "lte", "<=" -> "<=";
            default -> "=";
        };
    }

    private String findJoinHint(String fieldKey, ProfileConfiguration config) {
        // fieldKey = "entity.fieldName"; find relations leading to the field's table
        Field fieldDef = config.fields.get(fieldKey);
        if (fieldDef == null) return "";
        String targetTable = fieldDef.getTable();

        for (var entry : config.relations.entrySet()) {
            var joins = entry.getValue();
            if (joins == null || joins.isEmpty()) continue;
            var lastJoin = joins.get(joins.size() - 1);
            if (targetTable.equals(lastJoin.getSecond_table())) {
                StringBuilder hint = new StringBuilder();
                for (int i = 0; i < joins.size(); i++) {
                    var j = joins.get(i);
                    if (i > 0) hint.append(" → ");
                    hint.append(j.getFirst_table()).append(".").append(j.getFirst_field())
                        .append(" = ").append(j.getSecond_table()).append(".").append(j.getSecond_field());
                }
                return hint.toString();
            }
        }
        return "";
    }

    private String filterToCondition(Filter f) {
        if (f.getValues().size() == 1) {
            return f.getField() + " " + f.getType() + " '" + f.getValues().get(0) + "'";
        }
        return f.getField() + " " + f.getType() + " (" +
                f.getValues().stream().map(v -> "'" + v + "'").collect(Collectors.joining(", ")) + ")";
    }
}
