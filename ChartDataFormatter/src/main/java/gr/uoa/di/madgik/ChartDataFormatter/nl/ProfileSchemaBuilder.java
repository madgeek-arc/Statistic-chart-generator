package gr.uoa.di.madgik.ChartDataFormatter.nl;

import gr.uoa.di.madgik.statstool.domain.Filter;
import gr.uoa.di.madgik.statstool.mapping.Mapper;
import org.springframework.stereotype.Component;

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

    private String filterToCondition(Filter f) {
        if (f.getValues().size() == 1) {
            return f.getField() + " " + f.getType() + " '" + f.getValues().get(0) + "'";
        }
        return f.getField() + " " + f.getType() + " (" +
                f.getValues().stream().map(v -> "'" + v + "'").collect(Collectors.joining(", ")) + ")";
    }
}
