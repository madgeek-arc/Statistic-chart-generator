# Profiles

A profile is the logical schema between API callers and the physical DB. It defines entities, fields, and join paths. Multiple profiles can expose the same physical tables with different shapes.

---

## File Structure

```
mappings.json          ← registry: lists all profiles
  mapping.json         ← profile A: normalized (star schema)
  monitor.json         ← profile B: rich indicator fields
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
| `hidden` | If `true`, profile is not listed by `GET /schema/profiles` but still functional. |
| `file` | `classpath:` (JAR) or `file:` (filesystem) path to the mapping JSON. |

---

## Profile Mapping File

```json
{
  "entities":  [ <Entity>, ... ],
  "relations": [ <Relation>, ... ]
}
```

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
| `name` | Logical name used in API paths (e.g. `"publication"` in `"publication.year"`). |
| `key` | Primary key column. Used as the count target when entity appears alone in a field path. |
| `visible` | Default `true`. `false` hides entity from schema endpoints but keeps SQL generation working. |
| `filters` | Default WHERE conditions added to every query touching this entity. |
| `fields` | Exposed columns (see Field below). |
| `relations` | Names of joinable entities (informational; actual join paths in top-level `relations`). |

### Multiple entities on one table

```json
{ "from": "result", "name": "publication", "filters": [{ "column": "type", "type": "=", "values": ["publication"], "datatype": "text" }] },
{ "from": "result", "name": "dataset",     "filters": [{ "column": "type", "type": "=", "values": ["dataset"],     "datatype": "text" }] },
{ "from": "result", "name": "result"       }   ← no filter, exposes all rows
```

Querying `entity: "publication"` auto-injects `WHERE result.type = 'publication'` — no caller filtering needed.

---

## Field

```json
{ "column": "bestlicense", "name": "access mode", "datatype": "text", "visible": true }
```

| Field | Description |
|-------|-------------|
| `column` | Physical column name. |
| `name` | Logical name used in API paths (may contain spaces). |
| `datatype` | SQL cast hint: `"text"`, `"int"`, `"float"`, `"date"`, `"boolean"`, `"number"`. |
| `sqlTable` | Override which table this column lives on (see below). |
| `visible` | Default `true`. `false` hides from schema but works in SQL. |

### `sqlTable` — two patterns

**Forward hidden-join** — field lives on a satellite table not yet traversed:
```json
{ "column": "type", "name": "classification", "sqlTable": "result_classifications", "datatype": "text" }
```
Engine emits `JOIN result_classifications ON result.id = result_classifications.id`. Requires a matching `relations` entry.

**Denorm short-circuit** — column was moved to an ancestor table already in the query path:
```json
{ "column": "is_gold_oa", "name": "gold_oa", "sqlTable": "result", "datatype": "int" }
```
Engine skips intermediate joins and reads directly from `result`. Old API paths keep working unchanged.

---

## Relations

Defines physical join paths between tables. The engine uses these to resolve multi-hop field paths.

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

- `joins` is an ordered chain of single-step join conditions.
- Symmetric: defining `A → B` also registers `B → A` automatically.
- Bridge tables appear only in `joins` — never as entities.

**Generated SQL for `project.publication.year`:**
```sql
SELECT COUNT(DISTINCT p0.id), r1.year
FROM project p0
JOIN project_results j0 ON p0.id = j0.id
JOIN result r1          ON j0.result = r1.id
WHERE r1.type = 'publication'
GROUP BY r1.year
```

---

## Field Paths

| Form | Example | Resolves to |
|------|---------|-------------|
| `entity` | `"publication"` | `result.id` (key — used with aggregate) |
| `entity.field` | `"publication.year"` | `result.year` |
| `entity.field` (hidden-join) | `"publication.classification"` | `result_classifications.type` |
| `entity.relation.field` | `"project.publication.year"` | `result.year` via join |
| `entity.relation.relation.field` | `"org.project.publication.year"` | deep traversal |

---

## Visibility

| Mechanism | Scope | Effect |
|-----------|-------|--------|
| `hidden: true` in `mappings.json` | Entire profile | Not in `GET /schema/profiles`; still reachable by name |
| `visible: false` on entity | Single entity | Not in schema endpoints; SQL unaffected |
| `visible: false` on field | Single field | Not in entity field list; SQL unaffected |

Use `visible: false` to retire entities/fields without breaking existing chart URLs.

**Denormalization workflow:**
1. Move column to root table in DB.
2. Add `"sqlTable": "<root_table>"` to affected fields.
3. Set `"visible": false` on the satellite entity.
4. Restart. Old URLs work; schema no longer shows the satellite.

---

## Normalized vs. Flat Profiles

| | Normalized | Flat |
|--|------------|------|
| Schema | Entities map to real tables; JOINs at query time | Entities map to pre-joined views/materialized tables |
| SQL complexity | Higher (generated joins) | Lower (simple SELECT) |
| Flexibility | Any entity combination | Limited to pre-joined columns |
| Maintenance | Auto-propagates schema changes | Flat table must be rebuilt |

---

## Adding / Modifying Profiles

**New profile:** create mapping JSON → add entry to `mappings.json` → restart.

**New entity:** add to `entities[]` + add `relations` entries if needed → restart.

**New relation:** add to top-level `relations[]` (symmetric) → restart.

**Retire entity/field:** set `visible: false` → restart. Existing charts unaffected.

No hot-reload — restart required for any profile file change.
