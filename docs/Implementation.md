# Implementation

Architecture overview for contributors and operators.

---

## Module Structure

```
Application (WAR — Spring Boot entry point)
  └── ChartDataFormatter (JAR — REST controllers + chart formatters)
        └── DBAccess (JAR — SQL generation, caching, datasource management)
```

**Application** — bootstrap only (`appBoot.java`). Component-scans both sibling modules. Produces the deployable WAR at `Application/target/Statistic-chart-generator-Application-0.0.2-SNAPSHOT.war`.

**ChartDataFormatter** — REST controllers (`/chart`, `/table`, `/raw`, `/nl/*`), output formatters for HighCharts / GoogleCharts / ECharts, and `Mapper` (translates chart requests to `Query` objects using the profile mapping).

**DBAccess** — SQL generation (`SqlQueryBuilder` + `SqlQueryTree`), multi-datasource routing, HSQLDB result cache, schema/cache REST endpoints.

---

## Request Lifecycle

```
HTTP JSON request
  → controller (ChartDataFormatterRestController)
  → RequestBodyHandler.getChartQueries()     ← parse queries
  → StatsServiceImpl.query(queries, orderBy)
      ├─ single query  → runIndividually()
      └─ multi query (same profile) → CTE merge
           → SqlQueryBuilder.mapIntermediate()
           → SqlQueryTree.makeQuery()
           → StatsDBRepository.executeQuery()   ← cache check, DB execution
           → split result per series
  → formatter (HighChartsJsonFormatter / GoogleChartsJsonFormatter / …)
  → JSON response
```

---

## SQL Generation

### SqlQueryBuilder

Translates a `Query` object into an intermediate `SqlQueryTree` by:
1. Resolving entity and field names against the `ProfileConfiguration`
2. Mapping dot-path selects (`entity.relation.field`) to physical table columns
3. Injecting entity-level filters (e.g. `result.type = 'publication'`)
4. Building filter predicates per FilterGroup/op

### SqlQueryTree

Produces the final SQL string. Key behaviours:

- **Impala compatibility** — avoids scalar subqueries in SELECT (not supported by Impala). Cross-entity filters use EXISTS subqueries; OR filter groups on the root table use plain `(col=? OR col=?)`.
- **Numeric table aliases** — `r0`, `j0`, `t1`, `t2`, … to avoid SQL reserved word collisions.
- **CTEs** — complex queries with multiple join paths use WITH clauses.
- **EXISTS for AND filters** on related entities; plain OR predicates on root-table fields.

### Multi-Query Merging

Multiple queries in one request are merged into a single SQL using CTEs + FULL OUTER JOIN to reduce DB round-trips. The merge mode is controlled by `orderBy`:

```
WITH q1 AS (SELECT y1, x1 FROM … WHERE …),
     q2 AS (SELECT y2, x1 FROM … WHERE …),
     keys AS (SELECT x1 FROM q1 UNION SELECT x1 FROM q2)
SELECT keys.x1, q1.y1, q2.y2
FROM keys
LEFT JOIN q1 ON keys.x1 = q1.x1
LEFT JOIN q2 ON keys.x1 = q2.x1
ORDER BY …
```

Result is split back into per-series `Result` objects by the service layer.

See `DBAccess/docs/MergedQueries.md` for CTE examples.

---

## Priority Queue

All queries share a single `ThreadPoolExecutor` backed by a `PriorityBlockingQueue` (pool size **4**):

| Priority | Value | Used by |
|----------|-------|---------|
| `USER` | 0 | Every API caller (`/chart`, `/raw`, `/table`) |
| `CACHE_UPDATE` | 1 | `updateCache` shadow execution |
| `TRICKLE` | 2 | `trickleUpdate` background refresh |

Lower value = higher priority. User requests always preempt queued background tasks.

**Deduplication:** same `(sql, params, datasource)` triple already in-flight → second caller piggybacks on the existing `Future`.

---

## Cache

HSQLDB file-based persistent cache at `/tmp/cache`. Stores query results keyed by MD5 of `(sql, params, datasource profile)`.

Three-phase refresh cycle:

| Phase | Trigger | What happens |
|-------|---------|--------------|
| `updateCache` | Admin endpoint / scheduler | Executes queries against shadow datasource, stores results in `shadow` column. Entries remain `fresh=true` throughout. |
| `promoteCache` | Admin endpoint | `markAllStale` (sets `fresh=false`), then promotes `shadow → result` (`fresh=true`) for each entry that has a shadow. Entries without a shadow become trickle targets. Auto-starts `trickleUpdate`. |
| `trickleUpdate` | Auto-started by promoteCache | Background: refreshes stale entries one-by-one against main DB at `TRICKLE` priority. Stopped (not cancelled) when next `updateCache` begins. |

**Stale entries** (`fresh=false`) are treated as cache misses — `get()` returns null, caller re-executes against main DB, `save()` refreshes the entry. Counters (`total_hits`, `session_hits`) increment unconditionally on every `get()` call regardless of freshness.

See `DBAccess/docs/CacheLifecycle.md` for the full schema and admin endpoint reference.

---

## Datasource Routing

Multiple datasources configured in `application.yml` under `spring.datasources`. A thread-local `DatasourceContext` carries the active datasource ID per request. The custom `RoutingDataSource` resolves connections based on this context.

Example IDs: `monitor.public` (PostgreSQL), `openaire_stats` (Impala), `cache` (HSQLDB).

The shadow datasource for `updateCache` is resolved by appending `.shadow` to the profile name (e.g. `openaire_stats.shadow`).

---

## Profile System

Profiles are loaded at startup from `mappings.json`. Each profile mapping file defines:
- **Entities** — logical concepts mapped to physical tables (may share tables via entity filters)
- **Relations** — join chains between tables (symmetric; bridge tables not exposed as entities)
- **Fields** — columns with optional `sqlTable` override for hidden-joins or denorm short-circuits

`ProfileConfiguration` (compiled at startup) holds the full resolved mapping used by `SqlQueryBuilder`. No hot-reload — restart required after profile changes.

See `docs/Profiles.md` for the full configuration reference.

---

## Natural Language Queries

NL queries go through a multi-turn Claude agent conversation (`POST /nl/chat`). The agent has access to schema tools (`get_profiles`, `get_schema`, `get_field_values`, `validate_sql`) via an embedded MCP server.

On completion the agent produces a canonical NL string + HMAC signature. The signature covers `(profile, canonicalNl, canonicalFilters)` and is verified at `/chart` time before SQL execution — preventing unsigned queries from reaching the DB.

The signed query is cached in HSQLDB so `/nl/info` can resolve it to SQL without an LLM call.

See `docs/NaturalLanguageQueryDesign.md` for the full NL architecture.

---

## Key Classes

| Class | Module | Role |
|-------|--------|------|
| `ChartDataFormatterRestController` | ChartDataFormatter | Main chart/table/raw REST endpoints |
| `Mapper` | ChartDataFormatter | Translates `RequestInfo` → `Query` list using profile |
| `StatsServiceImpl` | DBAccess | Query routing, CTE merge, result splitting |
| `SqlQueryBuilder` | DBAccess | Intermediate representation from `Query` |
| `SqlQueryTree` | DBAccess | Final SQL string + parameter list |
| `StatsDBRepository` | DBAccess | DB execution, cache reads/writes |
| `CacheServiceImpl` | DBAccess | `updateCache`, `promoteCache`, `trickleUpdate` |
| `ProfileConfiguration` | DBAccess | Compiled entity/field/relation maps |
| `RoutingDataSource` | DBAccess | Thread-local datasource selection |

---

## Build & Run

```bash
mvn clean package -DskipTests   # build all modules
mvn clean package               # build with tests
mvn test -Dtest=SqlQueryTreeTest  # single test class
docker compose up -d            # port 8090
```

External config: `config/application.yml` with `--spring.config.location=file:/path/to/config/`.
