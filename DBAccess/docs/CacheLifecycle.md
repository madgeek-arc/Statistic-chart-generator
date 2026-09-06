# Cache Lifecycle

The cache stores query results in a persistent HSQLDB file database (`/tmp/cache`) and serves them directly to API callers, avoiding round-trips to the main datasource (PostgreSQL / Impala). This document describes the cache schema, the update/promote/trickle cycle, the priority queue, and the admin endpoints.

---

## Schema

```sql
CREATE TABLE cache_entry (
    key          VARCHAR(64)    NOT NULL,   -- MD5 of (sql, params, profile)
    result       LONGVARCHAR    NOT NULL,   -- JSON-serialised Result
    shadow       LONGVARCHAR,               -- pending result from shadow datasource
    query        LONGVARCHAR    NOT NULL,   -- JSON-serialised QueryWithParameters
    created      TIMESTAMP      DEFAULT NOW() NOT NULL,
    updated      TIMESTAMP      DEFAULT NOW() NOT NULL,
    total_hits   INT            DEFAULT 0  NOT NULL,  -- lifetime cache hits
    session_hits INT            DEFAULT 0  NOT NULL,  -- hits since last promote
    pinned       BOOLEAN        DEFAULT FALSE NOT NULL,
    exectime     INT            DEFAULT 0  NOT NULL,  -- last execution time (ms)
    queuetime    INT            DEFAULT 0  NOT NULL,  -- last queue wait (ms)
    profile      VARCHAR(255)   NOT NULL,
    fresh        BOOLEAN        DEFAULT FALSE NOT NULL  -- refreshed this cycle?
)
```

### Column semantics

| Column | Meaning |
|---|---|
| `key` | MD5 of the full SQL + bound parameters + datasource profile. Cache lookup key. |
| `result` | The current result served to API callers. Always non-null; can be stale. |
| `shadow` | Result pre-fetched against the shadow datasource during `updateCache`. Null when an entry was not reached in the current update cycle. |
| `fresh` | `true` = result is current (refreshed this cycle by promote or trickle, or from main DB via `save()`). `false` = stale: `get()` treats it as a cache miss and the caller re-executes against the main DB. Set at promote time. |
| `total_hits` | Incremented exclusively by `get()` (user-facing cache hits). Never reset. |
| `session_hits` | Same increment as `total_hits`, but reset to 0 at every `promoteCache`. Used to rank entries for the next update cycle. |
| `pinned` | Pinned entries sort first in the update queue, regardless of hit counts. |
| `shadow` | Cleared to null after `promoteCache` copies it to `result`. |

---

## Update / Promote / Trickle Cycle

The cache refresh process is split into three phases. Each can be triggered independently via admin endpoints.

### Phase 1 — `updateCache`

Populates the `shadow` column for each cached entry by executing the query against the **shadow datasource** (a parallel read replica or pre-release database identified by the `shadow` profile suffix, e.g. `openaire_stats.shadow`).

```
updateCache(profile, limit, maxSeconds):
  1. Signal any running trickle to stop (trickleStopRequested = true)
  2. Load entries sorted by: pinned DESC, session_hits DESC, total_hits DESC
  3. Execute in parallel (capped at `limit`, within `maxSeconds`):
       hit  → entry.shadow = result, entry.fresh = true
       skip → entry.shadow = null    (fresh unchanged — still served from cache)
  4. storeEntry() for every entry (MERGE: preserves total_hits/session_hits)
```

Entries remain `fresh=true` throughout this phase. Users continue getting cached results even for entries being shadow-executed. `fresh` is only reset at `promoteCache`.

### Phase 2 — `promoteCache`

Atomically moves the shadow results into the live `result` column.

```
promoteCache(profile):
  1. markAllStale(profile) — SET fresh=false for all entries in scope
  2. Load all entries (fresh=false in-memory)
  3. For each entry with shadow != null:
       result = shadow, shadow = null, fresh = true   ← promoted, now fresh
     For each entry with shadow = null (skipped):
       leave result unchanged, fresh stays false       ← trickle target
  4. storeEntry() for every entry
  5. resetSessionHits(profile) — zeroes session_hits for the next cycle
  6. → auto-starts trickleUpdate(profile) asynchronously
```

`markAllStale` runs here — not at the start of `updateCache` — so entries stay fresh and continue serving cached results throughout the entire (potentially long) update window. Only at promote time do skipped entries become stale.

### Phase 3 — `trickleUpdate`

Background process that refreshes stale (`fresh=false`) entries one-by-one against the **main datasource** (not the shadow). Runs at the lowest query priority so it never starves user requests.

```
trickleUpdate(profile):
  trickleStopRequested = false   ← cleared at start; next updateCache sets it true
  staleEntries = SELECT WHERE fresh=false ORDER BY total_hits DESC
  for each entry:
    if trickleStopRequested: break
    result = executeQuery(entry.query, MAIN_DB, priority=TRICKLE)
    UPDATE result=?, fresh=true, exectime=?, queuetime=? WHERE key=?
```

Ordering by `total_hits DESC` ensures the hottest stale entries are refreshed first, minimising the chance that a user request hits a stale entry.

Trickle is stopped (but not cancelled mid-query) when `updateCache` is called. It restarts automatically after the following `promoteCache`.

### Cycle timeline

```
t0  updateCache()    → shadows populated; entries stay fresh throughout
t1  promoteCache()   → markAllStale, then shadows→results (fresh=true), trickle auto-starts
t1+ trickleUpdate()  → background: stale (skipped) entries refreshed from main DB
t2  updateCache()    → trickle stopped, cycle repeats
```

---

## Counter Ownership

| Counter | Incremented by | Reset by |
|---|---|---|
| `total_hits` | `get()` only | Never |
| `session_hits` | `get()` only | `resetSessionHits()` at every `promoteCache` |

The update/promote/trickle cycle **never touches counters**. The `storeEntry()` MERGE UPDATE clause deliberately omits `total_hits` and `session_hits`. This ensures counters always reflect real user demand.

---

## Priority Queue

All queries (user, update, trickle) share a single `ThreadPoolExecutor` backed by a `PriorityBlockingQueue` with a pool size of **4** (admission control limit).

| Priority | Value | Used by |
|---|---|---|
| `USER` | 0 | Every API caller (`/chart`, `/raw`, `/table`) |
| `CACHE_UPDATE` | 1 | `updateCache` loop |
| `TRICKLE` | 2 | `trickleUpdate` loop |

Lower value = higher priority. A `USER` task submitted while the queue contains `CACHE_UPDATE` or `TRICKLE` tasks will be executed before them. Background tasks never starve user requests.

Deduplication: if the same `(sql, params, datasource)` triple is already in-flight, a second caller piggybacks on the existing `Future` rather than submitting a new task. This applies across all priorities.

---

## `save()` — User-triggered Cache Population

When a user request produces a cache miss (entry absent **or** entry stale), the result is written via `save()`:

```
save(query, result, execTime, queueTime):
  entry = new CacheEntry(key, query, result)
  entry.fresh = true      ← result is fresh from main DB
  storeEntry(entry)       ← MERGE: INSERT new or UPDATE existing (preserves counters)
```

The MERGE UPDATE clause never resets counters, so if an entry already exists (e.g. stale), its `total_hits`/`session_hits` are preserved.

---

## Admin Endpoints

All endpoints are on the `GET /cache/` path and require no request body.

| Endpoint | Parameters | Description |
|---|---|---|
| `GET /cache/updateCache` | `profile` (opt), `limit` (opt), `maxSeconds` (opt) | Start a shadow-DB update cycle. No-op if an update for the same profile is already running. If `limit`/`maxSeconds` are omitted, values from `application.yml` are used. |
| `GET /cache/stopUpdate` | — | Signal the running update loop to stop after the current parallel batch. No-op if nothing is running. |
| `GET /cache/promoteCache` | `profile` (opt) | Promote shadow results to live. Auto-starts `trickleUpdate` on completion. |
| `GET /cache/trickleUpdate` | `profile` (opt) | Manually trigger a trickle refresh of stale entries. No-op if trickle for the same profile is already running. Normally auto-started by `promoteCache`. |
| `GET /cache/dropCache` | `profile` (opt) | Delete all cache entries (or only those for the given profile). |
| `GET /cache/stats` | — | Return counts (total, fresh, stale, with_shadow), per-profile breakdown, and top-10 lists by total hits, session hits, and exec time. |

### Concurrency rules for `updateCache`

- Same profile already running → no-op
- Global update (`profile` omitted) already running → all new calls no-op
- Any profile update running when global is requested → global no-op
- Different profiles → run concurrently

### Concurrency rules for `trickleUpdate`

- Same profile already running → no-op
- Global trickle running → profile trickles no-op
- Any profile trickle running when global is requested → global no-op

---

## Configuration

```yaml
statstool:
  cache:
    enabled: true        # false disables all cache reads and writes
    update:
      entries: 5000      # default limit passed to updateCache
      time:    10800     # default maxSeconds passed to updateCache (3 hours)
```

The HSQLDB cache datasource must be configured separately:

```yaml
spring:
  datasources:
    - id: cache
      url: jdbc:hsqldb:file:/tmp/cache
      driver-class-name: org.hsqldb.jdbcDriver
      username: sa
      password:
```

The cache file persists across restarts. Schema migrations run automatically on startup (`postInit()`), so existing cache volumes are upgraded without data loss.

---

## `fresh` vs `session_hits` — Ranking Logic

At the start of each `updateCache`, entries are sorted by:

```
pinned DESC, session_hits DESC, total_hits DESC
```

`session_hits` (reset at each `promoteCache`) reflects demand **since the last cycle** — it ranks recently popular entries above historically popular but currently quiet ones. Entries beyond the `limit` keep their existing `fresh` flag during `updateCache` (no shadow written). At `promoteCache`, `markAllStale` sets them `fresh=false`; trickle then picks them up in `total_hits DESC` order.

---

## Stale Entry Behaviour

A stale entry (`fresh=false`) is treated as a **cache miss** by `get()`. The caller re-executes the query against the main datasource and calls `save()`, which refreshes the entry (MERGE UPDATE preserves counters, sets `fresh=true`).

This ensures users always receive current data — stale entries are never silently returned.

The trickle phase pre-emptively refreshes stale entries in the background so that subsequent user requests find them fresh. If a user request arrives before trickle reaches a given entry, the request itself performs the refresh (at `USER` priority).

The only way an entry is permanently removed from the cache is via `dropCache`.
