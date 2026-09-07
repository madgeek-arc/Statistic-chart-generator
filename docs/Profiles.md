# Profiles

A profile is the logical schema between API callers and the physical DB. It defines entities, fields, and join paths. Multiple profiles can expose the same physical tables with different shapes and field sets.

---

## File Structure

```
mappings.json          ← registry: lists all profiles
  mapping.json         ← profile A: normalized (star schema)
  monitor.json         ← profile B: rich indicator fields via hidden-joins
  openaire_db.json     ← profile C: hidden (admin-only)
```

Configure the registry path in `application.yml`:
```yaml
statstool:
  mappings:
    file:
      path: classpath:mappings.json
```

---

## `mappings.json` — Profile Registry

```json
[
  {
    "name":        "openaire",
    "description": "OpenAIRE information space",
    "primary":     true,
    "hidden":      false,
    "file":        "classpath:mapping.json"
  },
  {
    "name":    "monitor",
    "primary": false,
    "hidden":  false,
    "file":    "classpath:monitor.json"
  },
  {
    "name":   "internal",
    "hidden": true,
    "file":   "classpath:openaire_db.json"
  }
]
```

| Field | Description |
|-------|-------------|
| `name` | Key used in `query.profile`. Must be unique. |
| `primary` | If `true`, used when `query.profile` is omitted. Exactly one profile should be primary. |
| `hidden` | If `true`, profile not listed by `GET /schema/profiles` but still fully functional. |
| `file` | `classpath:` (JAR) or `file:` (filesystem) path to the mapping JSON. |

---

## Profile Mapping File

```json
{
  "entities":  [ <Entity>, ... ],
  "relations": [ <Relation>, ... ]
}
```

`entities` — logical concepts visible to API callers. `relations` — physical join paths between SQL tables used to resolve multi-hop field paths and generate JOINs / EXISTS subqueries.

---

## Entity

```json
{
  "from":    "result",
  "name":    "publication",
  "key":     "id",
  "visible": true,
  "filters": [
    { "column": "type", "type": "=", "values": ["publication"], "datatype": "text" }
  ],
  "fields": [
    { "column": "year",        "name": "year",        "datatype": "int"  },
    { "column": "bestlicense", "name": "access mode", "datatype": "text" }
  ],
  "relations": ["project", "datasource"]
}
```

| Field | Description |
|-------|-------------|
| `from` | Physical SQL table. Multiple entities may share one table (use `filters` to discriminate). |
| `name` | Logical name used in API paths (e.g. `"publication"` in `"publication.year"`). Must be unique within the profile. |
| `key` | Primary key column. Used as the count target when entity appears alone in a field path (e.g. `"publication"` → `result.id`). Also used as the join column in EXISTS subqueries. |
| `visible` | Default `true`. `false` hides entity from schema endpoints but keeps SQL generation working. |
| `filters` | Default WHERE conditions added to every query touching this entity (see [Entity Filters](#entity-filters)). |
| `fields` | Exposed columns (see [Field](#field)). |
| `relations` | Names of joinable entities. Used by schema endpoints to show navigation options; actual join paths defined in top-level `relations`. |

---

## Entity Filters

Entity filters are default WHERE conditions the engine adds to **every** query that touches the entity. They allow multiple logical entities to share one physical table.

### Multiple entities on one table

```json
{ "from": "result", "name": "publication", "filters": [{ "column": "type", "type": "=", "values": ["publication"], "datatype": "text" }] },
{ "from": "result", "name": "dataset",     "filters": [{ "column": "type", "type": "=", "values": ["dataset"],     "datatype": "text" }] },
{ "from": "result", "name": "software",    "filters": [{ "column": "type", "type": "=", "values": ["software"],    "datatype": "text" }] },
{ "from": "result", "name": "result"       }   ← no filter — exposes all rows
```

Querying `entity: "publication"` auto-injects `WHERE result.type = 'publication'` — no caller filtering needed.

Generated SQL:
```sql
SELECT COUNT(DISTINCT r0.id), r0.year
FROM result r0
WHERE r0.type = 'publication'
GROUP BY r0.year
ORDER BY r0.year;
```

### Filter deduplication

If multiple fields in the same query traverse the same entity, that entity's filters are added only once. The engine keys deduplication on `entityName + ":" + physicalTableName`, so renamed entities (e.g. `"publication"` mapping to `result`) are correctly deduplicated.

---

## Field

```json
{
  "column":   "bestlicense",
  "name":     "access mode",
  "datatype": "text",
  "sqlTable": null,
  "visible":  true
}
```

| Field | Description |
|-------|-------------|
| `column` | Physical column name. |
| `name` | Logical name used in API paths (may contain spaces, e.g. `"access mode"`). Must be unique within the entity. |
| `datatype` | SQL cast hint: `"text"`, `"int"`, `"float"`, `"date"`, `"boolean"`, `"number"`. |
| `sqlTable` | Override which table this column lives on (see [sqlTable](#sqltable)). |
| `visible` | Default `true`. `false` hides from schema but works in SQL. |

---

## `sqlTable`

Overrides which physical table a column is read from. Enables two opposite patterns:

### Pattern 1 — Forward hidden-join

Field lives on a satellite table not yet traversed. Engine emits a JOIN.

```json
// Entity "publication" (from: "result")
// "classification" physically lives on result_classifications
{
  "column":   "type",
  "name":     "classification",
  "sqlTable": "result_classifications",
  "datatype": "text"
}
```

Requires a matching entry in top-level `relations`. API path `"publication.classification"` → generated SQL:
```sql
JOIN result_classifications c0 ON r0.id = c0.id
-- SELECT / GROUP BY c0.type
```

### Pattern 2 — Denorm short-circuit

Column was moved to an ancestor table already traversed. Engine skips intermediate joins and reads directly from that ancestor.

```json
// Entity "indi_pub_gold" (from: "indi_pub_gold")
// is_gold_oa was moved onto the result table
{
  "column":   "is_gold_oa",
  "name":     "gold_oa",
  "sqlTable": "result",
  "datatype": "int"
}
```

API path `"result.indi_pub_gold.gold_oa"` → no JOIN emitted, column read from root alias:
```sql
-- Before denorm: JOIN indi_pub_gold i0 ON r0.id = i0.id → SELECT i0.is_gold
-- After denorm:  (no join)                               → SELECT r0.is_gold_oa
```

> **Caveat:** entity-level `filters` on the intermediate entity are silently dropped when short-circuiting. The denorm process is assumed to have baked those constraints into the data.

### Summary

| `sqlTable` value | Table traversal state | Effect |
|------------------|-----------------------|--------|
| absent / `null` | — | Column read from entity's own `from` table (normal) |
| names table B | B not yet traversed | Forward hidden-join: JOIN to B appended |
| names table A | A already traversed as ancestor | Denorm short-circuit: intermediate joins skipped, read from A |

---

## Relations

Defines physical join paths between tables. Drives JOIN and EXISTS generation for multi-hop field paths.

```json
{
  "from":  "result",
  "to":    "project",
  "joins": [
    { "from": "result",          "fromField": "id",     "to": "project_results", "toField": "result" },
    { "from": "project_results", "fromField": "id",     "to": "project",         "toField": "id" }
  ]
}
```

- `joins` is an **ordered** chain. The `to` of step N must match the `from` of step N+1.
- Symmetric: `A → B` automatically registers `B → A`. **Exception:** `from = to` (self-join) registers only the explicit direction.
- Bridge tables appear only in `joins` — never as entities.

**Two-step join: result → project (via bridge table)**
```
result ──(result.id = project_results.result)──► project_results
                                                         │
                                  (project_results.id = project.id)
                                                         ▼
                                                       project
```

**Single-step join: result → result_classifications**
```json
{
  "from":  "result",
  "to":    "result_classifications",
  "joins": [
    { "from": "result", "fromField": "id", "to": "result_classifications", "toField": "id" }
  ]
}
```

---

## Field Paths

Every `select` and `filter` uses a dot-separated path resolved left-to-right through the profile.

| Form | Example | Resolves to |
|------|---------|-------------|
| `entity` | `"publication"` | `result.id` (key — used with aggregate) |
| `entity.field` | `"publication.year"` | `result.year` |
| `entity.field` (hidden-join) | `"publication.classification"` | `result_classifications.type` after JOIN |
| `entity.relation.field` | `"project.publication.year"` | `result.year` via project → result join |
| `entity.relation.relation.field` | `"organization.project.publication.year"` | deep traversal |

### Examples with generated SQL

**Count publications per year** — direct field on root table:
```
entity: "publication"
select: ["publication" (count), "publication.year"]
```
```sql
SELECT COUNT(DISTINCT r0.id), r0.year
FROM result r0
WHERE r0.type = 'publication'
GROUP BY r0.year
ORDER BY r0.year;
```

**Count projects per funder where linked publications are open access** — cross-entity filter generates EXISTS:
```
entity:  "project"
select:  ["project" (count), "project.funder"]
filters: [{ field: "project.publication.access mode", type: "=", values: ["Open Access"] }]
```
```sql
SELECT COUNT(DISTINCT p0.id), p0.funder
FROM project p0
WHERE EXISTS (
  SELECT 1 FROM project_results s0
  JOIN result s1 ON s0.id = s1.id
  WHERE p0.id = s0.id
    AND s1.type = 'publication'       -- publication entity filter injected
    AND s1.bestlicense = ?
)
GROUP BY p0.funder
ORDER BY p0.funder;
```

**Datasource count per organisation country** — SELECT traverses two joins:
```
entity:  "datasource"
select:  ["datasource" (count), "datasource.organization.country"]
```
```sql
SELECT COUNT(DISTINCT d0.id), o2.country
FROM datasource d0
JOIN datasource_organizations j0 ON d0.id = j0.id
JOIN organization o2             ON j0.organization = o2.id
GROUP BY o2.country
ORDER BY o2.country;
```

---

## Visibility

Both entities and fields support a `visible` flag (default `true`) that controls schema discovery without affecting SQL generation.

| Mechanism | Scope | Effect |
|-----------|-------|--------|
| `hidden: true` in `mappings.json` | Entire profile | Not in `GET /schema/profiles`; still reachable by name |
| `visible: false` on entity | Single entity | Not in schema endpoints; pruned from other entities' `relations` lists; SQL unaffected |
| `visible: false` on field | Single field | Not in entity field list; SQL unaffected |

Existing chart URLs referencing invisible entities or fields **continue to work unchanged**.

---

## Patterns

### Normalized profile (`mapping.json`)

Each logical concept has its own table, joined via bridge tables (star/snowflake schema).

```
Physical schema                           Logical schema
─────────────────────────────             ──────────────────────
result (type='publication') ──────────► publication
result (type='dataset')     ──────────► dataset
result (type='software')    ──────────► software
result (no filter)          ──────────► (not exposed in this profile)
project                     ──────────► project
datasource                  ──────────► datasource
organization                ──────────► organization
result_classifications      ──────────► (satellite — hidden-join field on pub/dataset/software)

Bridge tables (not exposed as entities):
  project_results          (result ↔ project)
  result_datasources       (result ↔ datasource)
  result_organizations     (result ↔ organization)
  project_organizations    (project ↔ organization)
  datasource_organizations (datasource ↔ organization)
```

### Indicator-field profile (`monitor.json`)

Same physical tables, but a single `result` entity (no type filter) with dozens of fields — many forward hidden-joins to satellite indicator tables. Callers see flat fields; engine emits the JOINs transparently.

```json
{
  "from": "result",
  "name": "result",
  "key":  "id",
  "fields": [
    { "column": "year",              "name": "year",              "datatype": "int"    },
    { "column": "bestlicence",       "name": "access mode",       "datatype": "text"   },
    { "column": "green_oa",          "name": "green_oa",          "datatype": "int",
      "sqlTable": "indi_pub_green_oa"          },
    { "column": "is_gold",           "name": "gold_oa",           "datatype": "int",
      "sqlTable": "indi_pub_gold_oa"           },
    { "column": "has_abstract",      "name": "has_abstract",      "datatype": "int",
      "sqlTable": "indi_pub_has_abstract"      },
    { "column": "doi_from_crossref", "name": "doi_from_crossref", "datatype": "int",
      "sqlTable": "indi_pub_doi_from_crossref" },
    { "column": "citations",         "name": "citations",         "datatype": "number",
      "sqlTable": "result_citations_oc"        }
  ],
  "relations": ["project", "datasource", "organization", "topics", "result_fos", ...]
}
```

`result.gold_oa` generates `JOIN indi_pub_gold_oa i0 ON r0.id = i0.id` and selects `i0.is_gold`. The abstraction is total — callers never see the satellite tables.

### Flat profile (`mapping_flat.json`)

Each entity maps to a pre-joined view or materialized table. No relations needed; all columns are already co-located.

```json
{
  "from": "new_result_datasource_project",
  "name": "publication",
  "key":  "result_id",
  "filters": [
    { "column": "result_type", "type": "=", "values": ["publication"], "datatype": "text" }
  ],
  "fields": [
    { "column": "result_title",         "name": "title",                   "datatype": "text" },
    { "column": "project_acronym",      "name": "project_acronym",         "datatype": "text" },
    { "column": "datasource_name",      "name": "datasource_name",         "datatype": "text" },
    { "column": "project_funding_lvl0", "name": "project funding level 0", "datatype": "text" }
  ],
  "relations": []
}
```

| | Normalized | Flat |
|--|------------|------|
| Joins | Generated at query time | Pre-joined at ETL time |
| SQL | More complex | Simple SELECT |
| Flexibility | Any entity combination | Limited to pre-joined columns |
| Maintenance | Auto-propagates schema changes | Flat table must be rebuilt |

---

## Workflows

### Add a new profile

1. Create the mapping JSON (e.g. `classpath:my_profile.json`).
2. Add entry to `mappings.json`:
   ```json
   { "name": "my_profile", "primary": false, "hidden": false, "file": "classpath:my_profile.json" }
   ```
3. Restart.

### Add a new entity

1. Add to `entities[]`:
   ```json
   { "from": "my_table", "name": "my_entity", "key": "id",
     "fields": [{ "column": "col_a", "name": "field a", "datatype": "text" }],
     "relations": ["result"] }
   ```
2. Add `relations` entries if joinable to existing entities.
3. Restart.

### Add a new relation

```json
{
  "from":  "result",
  "to":    "my_satellite",
  "joins": [
    { "from": "result", "fromField": "id", "to": "my_satellite", "toField": "result_id" }
  ]
}
```

Registering `result → my_satellite` also gives you `my_satellite → result` for free.

### Retire an entity without breaking existing charts

Set `visible: false`. Old chart URLs keep working; new builders cannot see the entity in the schema.

```json
{
  "from":    "indi_pub_gold",
  "name":    "indi_pub_gold",
  "visible": false,
  "key":     "id",
  "fields": [
    { "column": "is_gold_oa", "name": "is_gold_oa", "datatype": "boolean", "sqlTable": "result" }
  ]
}
```

### Retire a field

```json
{ "column": "legacy_col", "name": "legacy_col", "datatype": "text", "visible": false }
```

### Denormalize a column

When a column moves from a satellite table into the root table:

**Before** — column on `indi_pub_gold_oa`, joined at query time:
```json
{ "column": "is_gold", "name": "gold_oa", "datatype": "int", "sqlTable": "indi_pub_gold_oa" }
```

**After** — column promoted to `result`:
```json
{ "column": "is_gold_oa", "name": "gold_oa", "datatype": "int", "sqlTable": "result" }
```

API path `"result.indi_pub_gold_oa.gold_oa"` continues to work. Generated SQL changes:
```sql
-- Before: JOIN indi_pub_gold_oa i0 ON r0.id = i0.id  →  SELECT i0.is_gold
-- After:  (no join)                                   →  SELECT r0.is_gold_oa
```

Full denorm workflow:
1. Move column to root table in DB.
2. Update `sqlTable` on affected fields to root table name.
3. Set `"visible": false` on the satellite entity.
4. Restart. Old URLs work; schema no longer shows the satellite.

---

## Validation Checklist

When adding or editing a profile:

- [ ] Every name in an entity's `relations[]` string list has a corresponding entry in `entities`.
- [ ] Every `sqlTable` value either (a) has a matching top-level `relations` entry (forward hidden-join) or (b) names an ancestor table already in the traversal path (denorm short-circuit).
- [ ] Exactly one profile in `mappings.json` has `"primary": true`.
- [ ] All `key` columns exist on the respective `from` tables.
- [ ] Multi-step `joins` arrays are ordered source → destination; `to` of step N matches `from` of step N+1.
- [ ] Entity names unique within profile. Field names unique within entity (duplicates silently overwrite each other).
- [ ] Invisible entities with active `filters` and `sqlTable` pointing to an ancestor: verify denorm preserved filter semantics in the data (entity filters are silently dropped during short-circuit).
- [ ] Fields marked `visible: false` that are referenced by existing chart URLs still have correct `column` / `sqlTable` config.

No hot-reload — restart required after any profile file change.
