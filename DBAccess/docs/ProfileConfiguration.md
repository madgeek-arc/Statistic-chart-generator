# Profile Configuration Reference

A **profile** is the logical schema that sits between the API caller and the physical database. It defines what entities exist, what fields they expose, and how to navigate from one entity to another. The same physical database can be presented through multiple profiles with different entity shapes, field selections, and visibility — without changing the API.

---

## Two-Level Structure

```
mappings.json          ← registry: lists all profiles
  └── mapping.json     ← profile A: entities + relations
  └── monitor.json     ← profile B: entities + relations
  └── openaire_db.json ← profile C (hidden): entities + relations
```

**`mappings.json`** is the entry point. It is an array of profile descriptors, each pointing to a separate mapping file.

**Profile mapping files** (e.g. `mapping.json`, `monitor.json`) define the actual schema: a list of `entities` and a list of `relations`.

---

## `mappings.json` — Profile Registry

### Full schema

```json
[
  {
    "name":         "OpenAIRE All-inclusive",
    "description":  "Contains all the OpenAIRE information space...",
    "usage":        "Best used to create statistics for the entire information space",
    "shareholders": ["All"],
    "complexity":   0,
    "primary":      true,
    "hidden":       false,
    "file":         "classpath:mapping.json"
  },
  {
    "name":         "monitor",
    "description":  "...",
    "shareholders": ["Admins", "Moderators"],
    "complexity":   0,
    "primary":      false,
    "hidden":       false,
    "file":         "classpath:monitor.json"
  },
  {
    "name":    "Stats db",
    "hidden":  true,
    "file":    "classpath:openaire_db.json"
  }
]
```

### Field reference

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | Profile key used in API requests (`query.profile`). Must be unique. |
| `description` | string | no | Human-readable description returned by the schema endpoint. |
| `usage` | string | no | Guidance note for API users on when to use this profile. |
| `shareholders` | string[] | no | Audience labels (informational only; not enforced by the engine). |
| `complexity` | integer | no | Informational complexity hint; not used by the engine. |
| `primary` | boolean | no | If `true`, this profile is used when `query.profile` is omitted. Exactly one profile should be primary. |
| `hidden` | boolean | no | If `true`, the profile is not returned by the `/schema/profiles` discovery endpoint but is still fully functional. Use for internal or admin-only profiles. |
| `file` | string | yes | Spring resource path to the mapping JSON file. `classpath:` prefix loads from the JAR classpath; `file:` loads from the filesystem. |

---

## Profile Mapping File — Schema Overview

```json
{
  "entities": [ <Entity>, ... ],
  "relations": [ <Relation>, ... ]
}
```

`entities` define the logical schema visible to API callers. `relations` define the physical join paths between SQL tables, used to resolve multi-hop field paths and generate JOINs / EXISTS subqueries.

---

## Entity

An entity is a named logical concept (e.g. `"publication"`, `"project"`) that may or may not map 1-to-1 to a physical SQL table.

### Full schema

```json
{
  "from":        "result",
  "name":        "publication",
  "key":         "id",
  "description": "Peer-reviewed journal articles and conference papers",
  "filters": [
    {
      "column":   "type",
      "type":     "=",
      "values":   ["publication"],
      "datatype": "text"
    }
  ],
  "fields": [
    { "column": "year",        "name": "year",        "datatype": "int"  },
    { "column": "bestlicense", "name": "access mode", "datatype": "text" },
    {
      "column":   "type",
      "name":     "classification",
      "sqlTable": "result_classifications",
      "datatype": "text"
    }
  ],
  "relations": ["project", "datasource", "organization"]
}
```

### Field reference

| Field | Type | Required | Description |
|---|---|---|---|
| `from` | string | yes | Physical SQL table name this entity reads from. Multiple entities can share the same `from` table (see [Multiple entities on one table](#multiple-entities-on-one-table)). |
| `name` | string | yes | Logical entity name used in API field paths (e.g. `"publication"` in `"publication.year"`). Must be unique within the profile. |
| `key` | string | yes | Primary key column on the `from` table. Used as the default column when the entity appears alone in a field path (e.g. `"publication"` → `result.id`). Also used as the join column when building EXISTS subqueries. |
| `description` | string | no | Human-readable description returned by the schema endpoint. |
| `filters` | Filter[] | no | Default WHERE conditions applied automatically to every query that touches this entity. See [Entity Filters](#entity-filters). |
| `fields` | Field[] | no | Columns exposed by this entity. See [Field](#field). |
| `relations` | string[] | no | Names of other entities this entity can be joined to. Used by the schema endpoint; the actual join paths are defined in `relations` at the mapping level. |

---

## Field

A field is one column exposed on an entity. It always appears in API paths as `"entityName.fieldName"`.

### Full schema

```json
{
  "column":      "bestlicense",
  "name":        "access mode",
  "datatype":    "text",
  "sqlTable":    null,
  "description": "Best available licence for this result"
}
```

### Field reference

| Field | Type | Required | Description |
|---|---|---|---|
| `column` | string | yes | Physical column name in the SQL table. |
| `name` | string | yes | Logical field name used in API paths (e.g. `"access mode"` → `"publication.access mode"`). May contain spaces. |
| `datatype` | string | yes | SQL type hint for parameter binding. One of: `"text"`, `"int"`, `"float"`, `"date"`, `"boolean"`, `"number"`. |
| `sqlTable` | string | no | Override which physical table this column lives on. See [The sqlTable Attribute](#the-sqltable-attribute). |
| `description` | string | no | Human-readable description returned by the schema endpoint. |

---

## Entity Filters

Entity filters are default WHERE conditions that the engine adds to **every** query that touches the entity. They are the mechanism that allows multiple logical entities to share one physical table.

### Example: three result types on one table

```json
{ "from": "result", "name": "publication",
  "filters": [{ "column": "type", "type": "=", "values": ["publication"], "datatype": "text" }] },

{ "from": "result", "name": "dataset",
  "filters": [{ "column": "type", "type": "=", "values": ["dataset"],     "datatype": "text" }] },

{ "from": "result", "name": "software",
  "filters": [{ "column": "type", "type": "=", "values": ["software"],    "datatype": "text" }] }
```

A query on `entity: "publication"` automatically gets `WHERE result.type = 'publication'` appended, even if the caller's request contains no filters at all.

### Filter schema

```json
{
  "column":   "type",
  "type":     "=",
  "values":   ["publication"],
  "datatype": "text"
}
```

| Field | Type | Description |
|---|---|---|
| `column` | string | Physical column name on the entity's `from` table. |
| `type` | string | Comparison operator. Same operators as API filter `type` values (`"="`, `"!="`, `">"`, `"between"`, `"contains"`, etc.). |
| `values` | string[] | Values to test against. |
| `datatype` | string | SQL type hint for parameter binding. |

### Deduplication

If multiple fields in the same query traverse the same entity, that entity's filters are added only once. The engine keys deduplication on `entityName + ":" + physicalTableName` so renamed entities (e.g. `"publication"` that maps to `result`) are correctly deduplicated against each other.

---

## Multiple Entities on One Table

A powerful pattern: several logical entities all map to the same physical table, distinguished only by entity-level filters.

```
Logical schema          Physical table
──────────────          ──────────────
publication  ──────┐
dataset      ──────┼──► result (filtered by type)
software     ──────┘
result       ──────────► result (no filter)
```

The `result` entity has no entity filters and exposes all rows. The `publication`, `dataset`, and `software` entities each restrict to their respective `type` values. This lets charts target specific result types without the caller needing to add type filters manually.

**Example query using the `publication` entity:**

```json
{
  "entity":  "publication",
  "select":  [{ "field": "publication", "aggregate": "count" },
              { "field": "publication.year", "aggregate": null }],
  "filters": []
}
```

Generated SQL (entity filter injected automatically):

```sql
SELECT COUNT(DISTINCT r0.id), r0.year
FROM result r0
WHERE r0.type = 'publication'
GROUP BY r0.year
ORDER BY r0.year;
```

---

## The `sqlTable` Attribute

The `sqlTable` attribute on a field overrides which physical table the column is read from. This enables two opposite patterns:

### Pattern 1 — Forward hidden-join

The field is declared on entity A but physically lives on table B (not yet traversed in the field path). The engine emits a JOIN from A's table to B.

**Use case:** surfacing a column from a satellite or extension table as if it were a direct field of the entity, without exposing the satellite as a separate entity.

```json
// Entity "publication" (from: "result")
// Field "classification" lives on result_classifications, not on result
{
  "column":   "type",
  "name":     "classification",
  "sqlTable": "result_classifications",
  "datatype": "text"
}
```

A relation entry for `result → result_classifications` must exist in the `relations` section so the engine knows the join condition.

API path `"publication.classification"` → generated SQL fragment:

```sql
JOIN result_classifications c0 ON r0.id = c0.id
-- SELECT c0.type
-- GROUP BY c0.type
```

### Pattern 2 — Denormalized field (reverse hidden-join)

The field is declared on entity B (the satellite) but after denormalization the column has been moved to an ancestor table A already in the traversal path. The engine short-circuits: it skips all intermediate joins and reads the column directly from A.

**Use case:** keeping the API schema stable after physically denormalizing a column into the root table.

```json
// Entity "indi_pub_gold" (from: "indi_pub_gold")
// is_gold_oa has been moved onto the result table
{
  "column":   "is_gold_oa",
  "name":     "is_gold_oa",
  "sqlTable": "result",
  "datatype": "boolean"
}
```

API path `"result.indi_pub_gold.is_gold_oa"` → generated SQL fragment:

```sql
-- No JOIN to indi_pub_gold emitted
-- Column read directly from root alias
WHERE r0.is_gold_oa = ?
```

The `relations` entry for `result → indi_pub_gold` may remain in place (for other non-denormalized fields on the same entity) or be removed if all fields have been migrated.

> **Caveat:** entity-level `filters` on the intermediate entity (e.g. `indi_pub_gold`) are silently dropped when short-circuiting. The denormalization process is assumed to have baked those constraints into the data. If the intermediate entity has active filters and the denorm copied all rows unconditionally, results will differ from the pre-denormalization baseline.

### Summary table

| `sqlTable` value | Table traversal state | Effect |
|---|---|---|
| absent / `null` | — | Column read from entity's own `from` table (normal) |
| names table B | B not yet traversed | Forward hidden-join: join to B is appended |
| names table A | A already traversed as ancestor | Denorm short-circuit: intermediate joins skipped, column read from A |

---

## Relations

Relations define how the engine navigates from one physical table to another. They drive JOIN and EXISTS generation for multi-hop field paths.

### Full schema

```json
{
  "from":  "result",
  "to":    "project",
  "joins": [
    { "from": "result",         "fromField": "id",     "to": "project_results", "toField": "result" },
    { "from": "project_results","fromField": "id",     "to": "project",         "toField": "id"     }
  ]
}
```

The `joins` array defines an **ordered chain** of join steps. Each step specifies a single join condition between two physical tables. Multiple steps in a chain traverse through intermediate tables (e.g. junction/bridge tables).

### Field reference

| Field | Type | Description |
|---|---|---|
| `from` (top-level) | string | Name of the logical entity the relation starts from. |
| `to` (top-level) | string | Name of the logical entity the relation ends at. |
| `joins[].from` | string | Physical table to join from. |
| `joins[].fromField` | string | Column on `joins[].from` to join on. |
| `joins[].to` | string | Physical table to join to. |
| `joins[].toField` | string | Column on `joins[].to` to join on. |

When `from` ≠ `to`, the engine automatically generates the **reverse** relation (`project → result`) so both directions can be traversed without duplicating the join definitions.

When `from` = `to` (self-join), only the explicitly defined direction is registered.

### Multi-step join example: result → project

```
result ──(result.id = project_results.result)──► project_results
                                                         │
                                  (project_results.id = project.id)
                                                         ▼
                                                       project
```

```json
{
  "from": "result",
  "to":   "project",
  "joins": [
    { "from": "result",          "fromField": "id",     "to": "project_results", "toField": "result" },
    { "from": "project_results", "fromField": "id",     "to": "project",         "toField": "id"     }
  ]
}
```

API path `"project.publication.year"` (project entity → publication entity → year field):

```sql
SELECT COUNT(DISTINCT p0.id), r1.year
FROM project p0
JOIN project_results j0 ON p0.id = j0.id
JOIN result r1 ON j0.result = r1.id
WHERE r1.type = 'publication'   -- publication entity filter injected
GROUP BY r1.year
ORDER BY r1.year;
```

### Single-step join example: result → result_classifications

```json
{
  "from": "result",
  "to":   "result_classifications",
  "joins": [
    { "from": "result", "fromField": "id", "to": "result_classifications", "toField": "id" }
  ]
}
```

This is the relation backing the `"classification"` forward hidden-join field on `publication` / `dataset` / `software`.

---

## Field Paths in API Requests

Every `select` and `filter` in an API query uses a dot-separated field path. The path is resolved by walking the entity graph left-to-right through the profile configuration.

### Path forms

| Path form | Example | Resolves to |
|---|---|---|
| `entity` | `"publication"` | `result.id` (entity's key column) — used with an aggregate |
| `entity.field` | `"publication.year"` | `result.year` (direct column) |
| `entity.field` (hidden-join) | `"publication.classification"` | `result_classifications.type` (after JOIN) |
| `entity.relation.field` | `"project.publication.year"` | `result.year` (traverses project → result) |
| `entity.relation.relation.field` | `"organization.project.publication.year"` | deeply nested traversal |

### Real examples from `mapping.json`

**Count publications per year:**
```
entity: "publication"
select: ["publication" (count), "publication.year"]
```
→ `SELECT COUNT(DISTINCT r0.id), r0.year FROM result r0 WHERE r0.type='publication' GROUP BY r0.year`

**Count projects per funder where linked publications are open access:**
```
entity:  "project"
select:  ["project" (count), "project.funder"]
filters: [{ field: "project.publication.access mode", type: "=", values: ["Open Access"] }]
```
→
```sql
SELECT COUNT(DISTINCT p0.id), p0.funder
FROM project p0
WHERE EXISTS (
  SELECT 1 FROM project_results s0
  JOIN result s1 ON s0.id = s1.id
  WHERE p0.id = s0.id AND s1.type='publication' AND s1.bestlicense = ?
)
GROUP BY p0.funder
ORDER BY p0.funder;
```

**Datasource count per organisation country:**
```
entity:  "datasource"
select:  ["datasource" (count), "datasource.organization.country"]
```
→
```sql
SELECT COUNT(DISTINCT d0.id), o2.country
FROM datasource d0
JOIN datasource_organizations j0 ON d0.id = j0.id
JOIN organization o2 ON j0.organization = o2.id
GROUP BY o2.country
ORDER BY o2.country;
```

---

## Full Working Example: `mapping.json`

The default profile (`OpenAIRE All-inclusive`) uses a **normalized schema**: each logical concept has its own table, joined via bridge tables. This is the cleanest design for a star/snowflake schema.

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

### Why bridge tables are not entities

Bridge tables are **not** listed in `entities`. They only appear inside `relations[].joins` entries. The engine uses them transparently when traversing paths; API callers never see them.

---

## Full Working Example: `monitor.json`

The `monitor` profile uses the **same physical tables** but exposes a much richer entity schema. A single `result` entity (no type filter) has dozens of fields, many of which are forward hidden-joins to satellite indicator tables (`indi_pub_*`, `indi_result_*`, etc.).

```json
// result entity in monitor.json (excerpt)
{
  "from": "result",
  "name": "result",
  "key":  "id",
  "fields": [
    { "column": "year",          "name": "year",          "datatype": "int"    },
    { "column": "bestlicence",   "name": "access mode",   "datatype": "text"   },
    { "column": "green_oa",      "name": "green_oa",      "datatype": "int",
      "sqlTable": "indi_pub_green_oa"         },
    { "column": "doi_from_crossref", "name": "doi_from_crossref", "datatype": "int",
      "sqlTable": "indi_pub_doi_from_crossref" },
    { "column": "is_gold",       "name": "gold_oa",       "datatype": "int",
      "sqlTable": "indi_pub_gold_oa"          },
    { "column": "has_abstract",  "name": "has_abstract",  "datatype": "int",
      "sqlTable": "indi_pub_has_abstract"     },
    { "column": "citations",     "name": "citations",     "datatype": "number",
      "sqlTable": "result_citations_oc"       }
  ],
  "relations": ["project", "datasource", "organization", "topics",
                "result_fos", "indi_result_has_cc_licence", ...]
}
```

This gives callers the illusion that `result.green_oa`, `result.gold_oa`, and `result.citations` are plain columns on `result`, when they are actually joins to separate indicator tables. The profile abstracts away the physical schema entirely.

---

## Flat Profile Pattern (`mapping_flat.json`)

A **flat profile** is one where each entity maps to a **pre-joined view or materialized table** that already contains all needed columns. There are no `relations` entries (or very few), and `sqlTable` is used occasionally for the few remaining satellite joins.

```json
// Flat profile entity — all columns live on one pre-joined table
{
  "from": "new_result_datasource_project",
  "name": "publication",
  "key":  "result_id",
  "filters": [
    { "column": "result_type", "type": "=", "values": ["publication"], "datatype": "text" }
  ],
  "fields": [
    { "column": "result_title",            "name": "title",            "datatype": "text" },
    { "column": "project_acronym",         "name": "project_acronym",  "datatype": "text" },
    { "column": "datasource_name",         "name": "datasource_name",  "datatype": "text" },
    { "column": "project_funding_lvl0",    "name": "project funding level 0", "datatype": "text" }
  ],
  "relations": []
}
```

**Trade-offs:**

| | Normalized profile | Flat profile |
|---|---|---|
| SQL complexity | JOINs generated at query time | Pre-joined at ETL time |
| Query performance | More complex SQL, optimizer-dependent | Simpler SQL, potentially faster |
| Flexibility | Any combination of entities filterable | Limited to columns on the flat table |
| Maintenance | Schema changes propagate automatically | Flat table must be rebuilt on schema change |

---

## How to Add a New Profile

1. Create the mapping JSON file (e.g. `classpath:my_profile.json`) following the schema above.
2. Add a descriptor entry to `mappings.json`:
   ```json
   {
     "name":         "my_profile",
     "description":  "...",
     "primary":      false,
     "hidden":       false,
     "file":         "classpath:my_profile.json"
   }
   ```
3. Restart the application. The new profile is immediately available under `query.profile = "my_profile"`.

The profile is loaded and compiled into a `ProfileConfiguration` at startup. There is no hot-reload; a restart is required after any profile file change.

---

## How to Add a New Entity to an Existing Profile

1. Open the profile mapping file.
2. Add an entry to `entities`:
   ```json
   {
     "from":    "my_table",
     "name":    "my_entity",
     "key":     "id",
     "fields":  [
       { "column": "col_a", "name": "field a", "datatype": "text" }
     ],
     "relations": ["result"]
   }
   ```
3. If the entity needs to be joinable to existing entities, add corresponding `relations` entries.
4. Restart.

---

## How to Add a New Relation

Relations are **symmetric** (unless `from` = `to`). Defining one relation entry covering `A → B` automatically gives you `B → A`.

```json
{
  "from":  "result",
  "to":    "my_new_satellite",
  "joins": [
    { "from": "result", "fromField": "id", "to": "my_new_satellite", "toField": "result_id" }
  ]
}
```

After adding this, any entity that has `"my_new_satellite"` in its `relations` list can use paths like `"result.my_new_satellite.some_col"`.

---

## How to Denormalize a Column

When a column moves from a joined satellite table into the root table:

**Before** — `is_gold_oa` lives on `indi_pub_gold_oa`, joined via `result.id = indi_pub_gold_oa.id`:
```json
{ "column": "is_gold", "name": "gold_oa", "datatype": "int",
  "sqlTable": "indi_pub_gold_oa" }
```

**After** — `is_gold_oa` is now a column directly on `result`:
```json
{ "column": "is_gold_oa", "name": "gold_oa", "datatype": "int",
  "sqlTable": "result" }
```

API path `"result.indi_pub_gold_oa.gold_oa"` continues to work unchanged. The generated SQL changes from:

```sql
-- Before
JOIN indi_pub_gold_oa i0 ON r0.id = i0.id
-- SELECT i0.is_gold
```

to:

```sql
-- After (no join)
-- SELECT r0.is_gold_oa
```

The `relations` entry for `result → indi_pub_gold_oa` can be removed once all fields on that entity have been denormalized, but leaving it is harmless.

---

## Configuration Validation Checklist

When building or editing a profile, verify:

- [ ] Every entity name in `relations[]` (the string list on an entity) has a corresponding entry in `entities`.
- [ ] Every `sqlTable` value on a field either (a) has a corresponding `relations` entry (forward hidden-join) or (b) is an entity table that will already be traversed as an ancestor (denorm short-circuit).
- [ ] Exactly one profile in `mappings.json` has `"primary": true`.
- [ ] All `key` columns actually exist on the respective `from` tables.
- [ ] Multi-step `joins` arrays are ordered from source to destination — the `to` of step N must match the `from` of step N+1.
- [ ] Entity names are unique within a profile.
- [ ] Field names are unique within an entity (duplicate `name` values within one entity's `fields` array will silently overwrite each other in the `ProfileConfiguration.fields` map).
