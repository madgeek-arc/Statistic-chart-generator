# Query Generation Pipeline

This document describes how an incoming chart/data request is translated into SQL and executed against the database.

---

## Overview

```
HTTP Request (JSON)
       │
       ▼
  RequestBodyHandler
       │  getChartQueries() + getOrderBy()
       ▼
  StatsServiceImpl.query(queryList, orderBy)
       │
       ├─ single query ──► runIndividually()
       │
       └─ multiple queries (same profile)
              │
              ▼
         SqlQueryBuilder (one per query)
              │  mapIntermediate()
              ▼
         SqlQueryTree
              │  makeQuery(parameters, orderBy)
              ▼
         individual SQL strings
              │
              ▼
         CTE merge in StatsServiceImpl
              │
              ▼
         StatsRepository.executeQuery()
              │
              ▼
         split merged result → List<Result>
```

---

## Stage 1 — Request Parsing

**Class:** `RequestBodyHandler`

The controller passes `requestJson.getChartQueries()` (a `List<Query>`) and `requestJson.getOrderBy()` (a `String`, may be null) to `StatsServiceImpl.query()`.

Each `Query` object carries:
- `entity` — logical entity name (e.g. `"publication"`)
- `profile` — profile key (e.g. `"ie_monitor"`)
- `select` — list of `Select` (field path + optional aggregate)
- `filters` — list of `FilterGroup` (each group has filters + op `"AND"` or `"OR"`)
- `limit` — max rows (0 = unlimited)
- `useCache` — whether to use the HSQLDB result cache

---

## Stage 2 — Multi-Query Routing (`StatsServiceImpl`)

**File:** `DBAccess/src/main/java/gr/uoa/di/madgik/statstool/services/StatsServiceImpl.java`

### Pre-scan
Before building any SQL, the service scans all queries to:
1. Verify all queries target the **same profile** — if not, falls back to `runIndividually()`.
2. Determine `xCount` — the number of grouping (non-aggregate) columns from `SELECT`, which must be consistent across all description-based queries. Inconsistency triggers `runIndividually()`.

### Single query / zero queries
Delegates directly to `runIndividually()` (see §3).

### Multiple queries (same profile)
Builds a merged CTE SQL. The merge mode is determined by `orderBy`:

| `orderBy` value | `stackedOrder` | x-axis driver | Outer ORDER BY |
|---|---|---|---|
| `null` | `true` | `keys` CTE (union of all queries) | `x1` (alphabetical) |
| `"xaxis"` | `false` | `q1` LEFT JOIN | `x1` |
| `"stacked"` | `true` | `keys` CTE | `COALESCE(y1,0)+…+COALESCE(yn,0) DESC` |
| `"pinned"` | `true` | `keys` CTE | `CASE WHEN y1 IS NOT NULL THEN 0 ELSE 1 END, COALESCE sum DESC` |
| `"yaxis"` / anything else | `false` | `q1` LEFT JOIN | `1 DESC` (first y column) |

**`"pinned"` use case:** q1 contains a fixed reference set (e.g. `country='Ireland'`) that must always appear in the output regardless of rank, followed by the remaining x-values (e.g. all other countries) sorted by combined sum descending. Because `stackedOrder=true`, the `keys` CTE unions x-values from all queries so q1's entries are guaranteed to appear. The `CASE WHEN y1 IS NOT NULL` expression sorts rows where q1 has data to position 0 (front), while rows present only in q2…qN sort to position 1 (back) ordered by combined sum.

#### `stackedOrder = false` (`"xaxis"` / `"yaxis"` / anything else)

```sql
WITH q1(y, x1) AS (<sql1 with ORDER BY+LIMIT>),
     q2(y, x1) AS (<sql2 stripped of ORDER BY>),
     ...
     qN(y, x1) AS (<sqlN stripped of ORDER BY>),
     t AS (
       SELECT q1.x1 AS x1, q1.y AS y1, q2.y AS y2, ..., qN.y AS yN
       FROM q1
         LEFT JOIN q2 ON q2.x1 = q1.x1
         ...
         LEFT JOIN qN ON qN.x1 = q1.x1
     )
SELECT y1, ..., yN, x1 FROM t
ORDER BY <x1 | 1 DESC>
[LIMIT <minLimit>]
```

**q1 defines the x-axis.** Its ORDER BY and LIMIT are preserved so it acts as the top-N anchor. q2…qN have their ORDER BY stripped and contribute their y-values for whatever x values q1 produced.

#### `stackedOrder = true` (`null` / `"stacked"` / `"pinned"`)

```sql
WITH q1(y, x1) AS (<sql1 stripped of ORDER BY>),
     ...
     qN(y, x1) AS (<sqlN stripped of ORDER BY>),
     keys AS (
       SELECT x1
       FROM (
         SELECT x1 FROM q1
         UNION ALL SELECT x1 FROM q2
         ...
         UNION ALL SELECT x1 FROM qN
       ) all_keys
       GROUP BY x1
     ),
     t AS (
       SELECT keys.x1 AS x1, q1.y AS y1, q2.y AS y2, ..., qN.y AS yN
       FROM keys
         LEFT JOIN q1 ON q1.x1 = keys.x1
         LEFT JOIN q2 ON q2.x1 = keys.x1
         ...
         LEFT JOIN qN ON qN.x1 = keys.x1
     )
SELECT y1, ..., yN, x1 FROM t
ORDER BY <x1 | COALESCE sum DESC>
[LIMIT <minLimit>]
```

**The `keys` CTE is the x-axis driver.** Every distinct x-value from every query appears in the output. Queries that have no data for a given x-value produce `NULL` for their y-column. This is essential for stacked categorical charts where each series returns a different subset of the x-axis (e.g. "Open Access" series only returns `bestlicence='Open Access'`).

#### LIMIT derivation
`minLimit` = the smallest positive `query.getLimit()` across all queries. This is appended as the outer `LIMIT`.

#### Result splitting
The merged result has columns `[y1, y2, …, yN, x1, …, xM]`. The service reconstructs N individual `Result` objects, each containing rows of the form `[yi, x1, …, xM]`.

#### Caching
If all queries have `useCache=true`, the merged SQL + parameters + profile are used as the cache key. A cache hit skips the database entirely.

---

## Stage 3 — Individual Query Execution (`runIndividually`)

Called for single queries, fallback from profile/xCount mismatch, or explicit per-query execution.

For each query:
1. If `query.getName() != null` → load named SQL from `NamedQueryRepository` (properties file), then resolve its parameters — see [Named Query Parameters](#named-query-parameters) below.
2. Otherwise → build SQL via `SqlQueryBuilder` + `SqlQueryTree`.
3. Check `StatsCache`; on miss, call `StatsRepository.executeQuery()`.

---

## Named Query Parameters

Named queries support two ways to bind request-supplied values into their SQL, resolved by
`StatsServiceImpl.resolveNamedQuery()` (called from both `runIndividually` and the multi-query
merge path). A single query must use only one of the two — see **Validation** below.

**`parameters` — original mechanism, kept for backward compatibility.** The SQL text uses raw
`?` placeholders; the request's `query.parameters` (`List<Object>`) is bound to them
**positionally**, in order. `StatsRepository` validates that the `?` count exactly matches
`parameters.size()` and rejects `null` values. This is how every named query worked before
`namedParameters` was added, and it still works exactly the same way today — existing named
queries and existing callers require no changes.

**`namedParameters` — new feature, added to support named/keyed and list-valued (`IN (...)`)
parameters.** The `?` mechanism above has no way to bind a variable-length list to a single
placeholder, which the original positional-only design couldn't express. `namedParameters`
solves this: the SQL text uses `:paramName` tokens (standard JDBC/Spring named-parameter syntax)
instead of `?`, and the request's `query.namedParameters` (`Map<String, Object>`) supplies the
values by name rather than by position. Resolution uses Spring's
`org.springframework.jdbc.core.namedparam.NamedParameterUtils` (already on the classpath via
`spring-jdbc`, pulled in transitively by `spring-boot-starter-data-jpa`) to rewrite the SQL back
down to plain `?` placeholders and build the matching value list — so from `StatsRepository`
downward, a `namedParameters`-resolved query is indistinguishable from a hand-written positional
one. A `List`/`Collection`-valued named parameter **automatically expands into one `?` per
element**, which is how `IN (:list)` clauses are supported — this only ever produces plain
positional `?` binds, so it works identically across Postgres, Impala, and HSQLDB (no
driver-specific SQL array type involved).

Whichever form is used, resolution always produces a plain SQL string with positional `?` marks
plus a matching ordered `List<Object>`, which is what `StatsRepository.ResultCallable.call()`
binds via JDBC `PreparedStatement.setObject()` — so downstream execution, `StatsCache`
cache-key computation, and the CTE-merge logic never need to know which form was used.

Example — the SQL text:
```sql
WHERE t0.domain = :domain
  AND t0.technology_l3 IN (:technologiesL3)
```
resolves (given `{"domain": "Digital Twins", "technologiesL3": ["AI", "IoT"]}`) to:
```sql
WHERE t0.domain = ?
  AND t0.technology_l3 IN (?, ?)
```
with parameters `["Digital Twins", "AI", "IoT"]`.

Request body:
```json
{"series":[{"query":{"name":"sciance.f61","profile":"sciance",
  "namedParameters": {"domain": "Digital Twins", "technologiesL3": ["AI", "IoT"]}
}}]}
```

Note this is distinct from the `${key}` syntax already used in some named-query SQL texts
(e.g. `namedqueries.properties` entries) — that is resolved once at *query-load time* by
`NamedQueryRepository`, against other keys in the same properties file, never from the request
body. It exists for values shared statically across queries, and `${...}` is also reserved
syntax for Hive/Impala's own server-side variable substitution, so it's kept separate from
`namedParameters`.

**Validation** — four `namedParameters` mistakes are checked upfront, before any SQL is built,
and all throw `NamedParametersValidationException`:
- providing both `parameters` and `namedParameters` on the same query,
- an empty-list-valued named parameter (which would produce an invalid `IN ()`),
- a `:name` token referenced in the SQL text with no matching entry in `namedParameters` — all
  missing names are collected and reported together, not just the first one,
- a `namedParameters` key not referenced by any `:name` token in the SQL text (catches typos in
  the request) — likewise all unreferenced keys are collected and reported together.

`NamedParametersValidationException` is a request/input error, distinct from other failures:
`StatsServiceImpl.query()` still wraps it as `StatsServiceException` like everything else, but
`RequestBodyHandler` (`ChartDataFormatter`) specifically recognizes it via `getCause()` and
surfaces it as **`400 Bad Request`** with the specific reason in the response body
(`{"error": "..."}`), instead of the generic `422 Unprocessable Entity` (empty body) given to
every other `StatsServiceException`.

---

## Stage 4 — SQL Building (`SqlQueryBuilder`)

**File:** `DBAccess/src/main/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryBuilder.java`

`SqlQueryBuilder` translates the logical `Query` (entity/field names from the profile schema) into a concrete `Query` with physical SQL paths, then hands it to `SqlQueryTree`.

### mapField(field)

Each logical field reference is a dot-separated path, e.g. `"publication.topics.result.result_fos.lvl2"`. The method walks the path segments against the `ProfileConfiguration`, maintaining a `tableToPath` map (`physicalTableName → pathPrefixAtThatPoint`) as it traverses hops.

- **Size 1** (entity key only, e.g. `"publication"`):
  `path = tableName + "." + keyColumn`
  → calls `addEntityFilters(entity, tableName)`

- **Size 2** (entity + direct field, e.g. `"publication.bestlicence"`):
  `path = tableName` then `path += "." + column`
  → If the field's `sqlTable` differs from the entity table, checks `tableToPath` first (see below).
  → calls `addEntityFilters(entity, tableName)`

- **Size ≥ 3** (multi-hop, e.g. `"publication.topics.result.result_fos.lvl2"`):
  Iterates intermediate segments, accumulating an encoded path string of the form `TableA(col_a).(col_b)TableB`.
  → calls `addEntityFilters(entity, path)` for each intermediate table.
  → records each reached physical table in `tableToPath` before extending the path.

The returned path string encodes the full join chain using the syntax `(fromCol).(toCol)TableName` as segments.

#### Field `sqlTable` — two directions

A `MappingField` in the profile JSON can declare a `sqlTable` that differs from its entity's own `from` table. This is used for two opposite purposes:

**Forward hidden-join** (existing): `sqlTable` names a table not yet in the traversal path. `mapField` calls `joinTables()` to append an extra join segment, making the field appear to belong to entity A while physically living on table B.

```json
// publication entity (from: "result"), classification field lives on result_classifications
{ "column": "type", "name": "classification", "sqlTable": "result_classifications" }
```
→ path: `result(id).(id)result_classifications.type`

**Denormalized field (reverse hidden-join)**: `sqlTable` names a table that is an **ancestor already recorded in `tableToPath`**. `mapField` short-circuits: it discards all intermediate join segments accumulated since that ancestor and sets `path = tableToPath.get(field.sqlTable)`, then appends the column. No join to the intermediate entity is emitted. `addEntityFilters` for the intermediate entity is also skipped.

```json
// indi_pub_gold entity (from: "indi_pub_gold"), is_gold_oa denormalized onto result
{ "column": "is_gold_oa", "name": "is_gold_oa", "sqlTable": "result" }
```
→ path: `result.is_gold_oa` (even though the logical path is `result.indi_pub_gold.is_gold_oa`)

This allows a schema to remain unchanged after physical denormalization: users still reference `result.indi_pub_gold.is_gold_oa` and the generated SQL reads `result.is_gold_oa` directly.

**Important:** entity-level `filters` declared on the intermediate entity (e.g. `indi_pub_gold`) are silently dropped when the field short-circuits to an ancestor. The denormalization process is assumed to have baked in any constraints those filters represented. If the intermediate entity has active filters and the denorm copied all rows unconditionally, query results will differ from the pre-denormalization baseline.

### addEntityFilters(entity, path)

Adds entity-level table filters (e.g. `type='publication'` for the `publication` entity) to the shared `entityFilters` list. Deduplication via `entityFiltersSeen` keyed on `entity + ":" + tableName` prevents the same filter appearing multiple times when multiple fields traverse the same entity. The `tableName` (from `table.getTable()`) is used consistently, not the logical entity name.

### mapIntermediate()

After mapping all select and filter fields:
1. Collects mapped `Select` objects.
2. Collects mapped `FilterGroup` objects (user filters).
3. Appends the entity-level `FilterGroup` (AND group of entity filters).
4. Returns a new `Query` with the physical table name as entity, physical paths in selects/filters, and the original limit/orderBy/useCache.

---

## Stage 5 — SQL Tree Construction (`SqlQueryTree`)

**File:** `DBAccess/src/main/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryTree.java`

`SqlQueryTree` builds the final SQL string. The constructor builds a **join tree** (rooted at the entity table) from the select paths. Filter paths are stored separately and processed during `makeQuery()`.

### Join Tree (for SELECT only)

The tree is a rooted DAG of `Node` objects. Each node represents a SQL table with an alias (`r0`, `p1`, `o2`, …). `addSelect()` and `addEdge()` walk the encoded path to add join edges.

Selects are categorised:
- **Root + aggregate** (e.g. `COUNT(DISTINCT r0.id)`) → direct expression in SELECT
- **Root + no aggregate** (e.g. `r0.bestlicence`) → expression in SELECT + GROUP BY
- **Non-root + no aggregate** (e.g. `p1.category`) → direct `JOIN` to the child table, column in SELECT + GROUP BY
- **Non-root + aggregate** (e.g. `SUM(d.value)`) → derived `LEFT JOIN` subquery (Impala requires this; see below)

### makeQuery — SELECT clause

The method traverses the join tree via a stack and emits:

**Root selects** are straightforward:
```sql
COUNT(DISTINCT r0.id)   -- aggregate
r0.bestlicence          -- non-aggregate → also added to GROUP BY
```

**Non-root non-aggregate selects** generate a direct `JOIN`:
```sql
JOIN project_results p1 ON r0.id = p1.result_id
-- p1.category added to SELECT and GROUP BY
```

**Non-root aggregate selects** generate a derived `LEFT JOIN` subquery (Impala incompatibility: Impala cannot use scalar subqueries in SELECT):
```sql
LEFT JOIN (
  SELECT t1.result_id AS k, SUM(t1.value) AS c3
  FROM project_results t1
  GROUP BY t1.result_id
) d1 ON r0.id = d1.k
-- SUM(d1.c3) or d1.c3 used in outer SELECT depending on context
```
Aliases inside derived subqueries use numeric names (`t1`, `t2`, …) to avoid SQL reserved word collisions.

### makeQuery — WHERE clause (`mapFilters`)

Each `FilterGroup` is processed independently. Filter groups are joined with `AND` in the outer WHERE.

#### AND filter group (default)

Each filter in the group is processed individually:

- **Root-level filter** (no hops, e.g. `r0.year >= ?`):
  ```sql
  r0.year >= ?
  ```

- **Single-hop filter on a directly-joined table** (i.e. the target table is already JOINed for a GROUP BY SELECT field):
  The predicate is applied **inline** on the existing JOIN alias:
  ```sql
  d1.type != ?
  ```
  Using `EXISTS` here would be incorrect: `EXISTS (SELECT 1 FROM t WHERE corr AND col != 'X')` is `TRUE` whenever *any* row doesn't match, so excluded values would still appear in the GROUP BY via the direct JOIN. Applying the filter directly on the alias correctly excludes those rows.

- **Single-hop filter on a non-directly-joined table** (e.g. filter on `result_refereed.refereed`):
  ```sql
  EXISTS (
    SELECT 1 FROM result_refereed s0
    WHERE r0.id = s0.id AND s0.refereed = ?
  )
  ```
  Multi-hop (e.g. `result_topics → result → result_fos`):
  ```sql
  EXISTS (
    SELECT 1 FROM result_topics s0
    JOIN result s1 ON s0.id = s1.id
    JOIN result_fos s2 ON s1.id = s2.id
    WHERE r0.id = s0.id AND s2.lvl1 = ?
  )
  ```

#### OR filter group

Filters in the group are partitioned by whether they involve hops:

**Root-level predicates** (no hops) are combined with `OR`:
```sql
(r0.type = ? OR r0.type = ?)
```

**Single hop-based predicate** emits a direct correlated EXISTS:
```sql
EXISTS (SELECT 1 FROM result_refereed s0 WHERE r0.id = s0.id AND s0.refereed = ?)
```

**Multiple hop-based predicates** emit a single EXISTS over a `UNION ALL` derived table:
```sql
EXISTS (
  SELECT 1 FROM (
    SELECT s0.id AS rid FROM result_refereed s0 WHERE s0.refereed = ?
    UNION ALL
    SELECT s0.id AS rid FROM indi_result_oa_with_license s0 WHERE s0.oa_with_license = ?
  ) u WHERE u.rid = r0.id
)
```

> **Impala constraint:** OR filter groups that span different columns on the same root table use plain `(col = ? OR col = ?)` predicates. Impala cannot resolve correlated column references inside `UNION ALL` subqueries, so the old `EXISTS (SELECT rid …) u WHERE u.rid = r0.id` form is avoided for root-level predicates.

#### Predicate types

| Filter type | Generated SQL |
|---|---|
| `=` (single value) | `col = ?` |
| `=` (multiple values) | `col IN (?, ?, ?)` |
| `!=` (single value) | `col != ?` |
| `!=` (multiple values) | `col NOT IN (?, ?)` |
| `in` | `col IN (?, ?, ?)` |
| `not_in` | `col NOT IN (?, ?, ?)` |
| `>`, `>=`, `<`, `<=` | `col > ?` etc. |
| `between` | `col BETWEEN ? AND ?` |
| `contains` | `lower(col) LIKE CONCAT('%', ?, '%')` |
| `starts_with` | `lower(col) LIKE CONCAT(?, '%')` |
| `ends_with` | `lower(col) LIKE CONCAT('%', ?)` |
| `is_null` | `col IS NULL` |
| `is_not_null` | `col IS NOT NULL` |

`in`/`not_in` always emit `IN` / `NOT IN`, regardless of value count (a
single-value `in` renders as `col IN (?)`), unlike `=` / `!=` which only switch
to `IN` / `NOT IN` when given multiple values.

`is_null`/`is_not_null` bind no JDBC parameters and ignore `values`. Note that
on a hop-based filter (a field on a related/joined entity), the predicate is
applied inside an `EXISTS (...)` correlated subquery — `is_null` there means
"a related row exists and its column is null", not "no related row exists at
all". This is the existing EXISTS semantics shared by every operator on hop
fields, not specific to these two.

### makeQuery — GROUP BY and ORDER BY

`GROUP BY` includes all non-aggregate expressions (both root and non-root direct joins).

`ORDER BY`:
- `null` or `"xaxis"` → `ORDER BY <all GROUP BY columns>` (typically the x-axis column alphabetically)
- anything else → `ORDER BY <first aggregate expression> DESC`

`LIMIT` is appended if `query.getLimit() != 0`.

---

## Encoded Path Format

`SqlQueryBuilder.mapField()` returns a path string that encodes the full join chain. The format is:

```
TableA(fromCol).(toCol)TableB(fromCol).(toCol)TableC.targetColumn
```

Example for `publication.topics.result.result_fos.lvl2`:
```
result(id).(id)result_topics(id).(id)result(id).(id)result_fos.lvl2
```

`SqlQueryTree` parses this by splitting on `.` and reading the `(col)` tokens to reconstruct hops.

---

## Parameter Binding

Parameters are collected into a `List<Object>` in the order predicates are emitted. `mapType()` converts string values from the request to the appropriate Java type (`Integer`, `Float`, `String`) based on the field's `datatype` from the profile configuration. This ensures Impala receives correctly-typed JDBC parameters.

---

## Profile Configuration

Loaded at startup from the JSON mapping file (e.g. `openaire.json`). The `ProfileConfiguration` holds:

- `tables` — keyed by logical entity name (`"publication"`), value is `Table(sqlTable, keyColumn, entityFilters)`
- `fields` — keyed by `"entityName.fieldName"`, value is `Field(sqlTable, column, datatype)`
- `relations` — keyed by `"TableA.TableB"`, value is the ordered list of `Join` objects connecting them

Entity-level filters (e.g. `type = 'publication'` on the `publication` entity) are added automatically to every query that uses that entity; they are deduplicated so each entity contributes its filters only once per SQL query.

### MappingField `sqlTable` attribute

The optional `sqlTable` attribute on a field in the profile JSON controls which physical table the column is read from:

| `sqlTable` value | Effect |
|---|---|
| Absent | Column read from the entity's own `from` table (normal case) |
| Names a table **not yet traversed** in the path | Forward hidden-join: a join segment to that table is appended (field appears on entity A, lives on table B) |
| Names a table **already traversed** as an ancestor | Denormalized field: all intermediate joins are skipped and the column is read directly from the ancestor table |

**Denormalization migration pattern:** when a column moves from a joined satellite table into the root table, add `"sqlTable": "<root_table>"` to the field definition in every affected entity. The logical schema (entity + field names) is unchanged; only the generated SQL changes. The `relations` entry for the satellite can be left in place for other non-denormalized fields on that entity, or removed if all fields have been migrated.

```json
// Before: result.indi_pub_gold.is_gold_oa joins to indi_pub_gold table
{
  "from": "indi_pub_gold", "name": "indi_pub_gold", "key": "id",
  "fields": [
    { "column": "is_gold_oa", "name": "is_gold_oa", "datatype": "boolean" }
  ]
}

// After: same logical path, column now read directly from result
{
  "from": "indi_pub_gold", "name": "indi_pub_gold", "key": "id",
  "fields": [
    { "column": "is_gold_oa", "name": "is_gold_oa", "datatype": "boolean", "sqlTable": "result" }
  ]
}
```
