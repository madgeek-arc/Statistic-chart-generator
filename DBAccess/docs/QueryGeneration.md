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
1. If `query.getName() != null` → load named SQL from `NamedQueryRepository` (properties file).
2. Otherwise → build SQL via `SqlQueryBuilder` + `SqlQueryTree`.
3. Check `StatsCache`; on miss, call `StatsRepository.executeQuery()`.

---

## Stage 4 — SQL Building (`SqlQueryBuilder`)

**File:** `DBAccess/src/main/java/gr/uoa/di/madgik/statstool/mapping/SqlQueryBuilder.java`

`SqlQueryBuilder` translates the logical `Query` (entity/field names from the profile schema) into a concrete `Query` with physical SQL paths, then hands it to `SqlQueryTree`.

### mapField(field)

Each logical field reference is a dot-separated path, e.g. `"publication.topics.result.result_fos.lvl2"`. The method walks the path segments against the `ProfileConfiguration`:

- **Size 1** (entity key only, e.g. `"publication"`):
  `path = tableName + "." + keyColumn`
  → calls `addEntityFilters(entity, tableName)`

- **Size 2** (entity + direct field, e.g. `"publication.bestlicence"`):
  `path = tableName` then `path += "." + column`
  → If the field's `sqlTable` differs from the entity table, appends an encoded join segment first.
  → calls `addEntityFilters(entity, tableName)`

- **Size ≥ 3** (multi-hop, e.g. `"publication.topics.result.result_fos.lvl2"`):
  Iterates intermediate segments, accumulating an encoded path string of the form `TableA(col_a).(col_b)TableB`.
  → calls `addEntityFilters(entity, path)` for each intermediate table.

The returned path string encodes the full join chain using the syntax `(fromCol).(toCol)TableName` as segments.

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
| `>`, `>=`, `<`, `<=` | `col > ?` etc. |
| `between` | `col BETWEEN ? AND ?` |
| `contains` | `lower(col) LIKE CONCAT('%', ?, '%')` |
| `starts_with` | `lower(col) LIKE CONCAT(?, '%')` |
| `ends_with` | `lower(col) LIKE CONCAT('%', ?)` |

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
