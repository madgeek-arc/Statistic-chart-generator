# Natural Language Query — Design Document

## Overview

Users describe their data in plain English. An LLM-driven agent refines the description through conversation, then produces a signed canonical NL query. The frontend embeds the canonical NL + HMAC signature inside a normal chart request. The backend verifies the signature, generates SQL (with caching), and returns chart data through the existing pipeline.

The JSON DSL and named queries remain fully supported with no breaking changes.

---

## Goals

- Allow users to describe data queries in natural language instead of the JSON DSL.
- Cache LLM-generated SQL to avoid redundant API calls.
- Prevent abuse of LLM API tokens via HMAC-signed NL queries.
- Keep LLM integration behind interfaces so implementations (Claude, other models) are swappable.
- Expose reusable MCP tools so other portals can build their own agentic UX on top of the same backend.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Angular UI (or other portal)                       │
│  POST /nl/chat  ──────────────────────────────────► │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  NlChatController  →  NlQueryAgent (interface)      │
│  Manages in-memory session (configurable TTL)       │
│  Drives agent loop via pluggable LLM backend        │
└─────────────────────────────────────────────────────┘
                        │  tool calls
                        ▼
┌─────────────────────────────────────────────────────┐
│  MCP Tools (same application, NlMcpTools)           │
│  ├── get_schema(profile)                            │
│  ├── get_field_values(profile, field, limit)        │
│  ├── validate_sql(profile, sql)                     │
│  └── sign_nl_query(profile, canonical_nl)           │
└─────────────────────────────────────────────────────┘

  ── conversation done → frontend POSTs /chart with query.nl+sig ──►

┌─────────────────────────────────────────────────────┐
│  RequestBodyHandler  →  NlQueryService              │
│  Verify HMAC → cache lookup → NlSqlGenerator        │
│  → SqlSafetyValidator → execute → format chart      │
└─────────────────────────────────────────────────────┘
```

Other portals connect directly to the MCP server and manage their own conversation loop.

---

## Conversation Flow (Angular UI)

1. User sends a natural language message to `POST /nl/chat`.
2. Backend delegates to `NlQueryAgent`, which calls `get_schema` or `get_field_values` (via MCP tools) to ground its responses in the actual profile.
3. Agent replies with clarifications or suggestions. Backend returns `{ reply, sessionId, done: false }`.
4. Conversation continues until the agent is satisfied with the NL description.
5. Agent calls `sign_nl_query(profile, canonical_nl)` internally — detected via a thread-local flag.
6. Backend returns `{ reply, sessionId, done: true, canonicalNl: "...", sig: "<hmac>" }`.
7. Session is discarded (in-memory, no persistence).

Session state is held in-memory per JVM instance. A configurable TTL evicts idle sessions.

---

## Chart Request Flow

The frontend flow is identical across all query types and all three data endpoints:

| Endpoint | GET | POST |
|----------|-----|------|
| `/chart` | Serves HTML + JS bundle | Returns chart JSON — **NL supported** |
| `/table` | Serves table HTML template | Returns table JSON — **NL supported** |
| `/raw`   | Returns raw data — **NL supported** | — |

For `/chart` and `/table`:
1. `GET` — loads the page (static, no data).
2. JS constructs a request body and sends `POST` — receives formatted JSON.
3. Chart / table renders.

The `nl`/`sig`/`profile` fields live inside `query{}` in the JSON body, so all three endpoints handle NL queries transparently — GET (`json=` blob) and POST alike — with no special routing needed.

Once the conversation is done, the frontend constructs a standard `POST /chart` body.
The `query` field inside each `chartsInfo` entry is a discriminated union:

### NL query (new)

```json
{
  "library": "HighCharts",
  "chartsInfo": [{
    "type": "bar",
    "name": "Publications per year",
    "query": {
      "nl": "Number of open access publications per year",
      "sig": "<hmac>",
      "profile": "openaire_stats"
    }
  }]
}
```

### Named query (unchanged)

```json
{
  "query": {
    "name": "publications.per_year"
  }
}
```

### JSON DSL (unchanged)

```json
{
  "query": {
    "entity": "publication",
    "select": [...],
    "filters": [...]
  }
}
```

### Dispatch priority in `RequestBodyHandler`

`RequestBodyHandler` iterates over `chartsInfo` and dispatches each chart independently:

1. `query.nl` is non-blank → **NL path** (verify sig, cache lookup, SQL generation)
2. `query.name` is non-blank → **named query** path
3. Otherwise → **JSON DSL** path

DSL and named queries are still batched together into a single SQL statement (CTE merging preserved). NL queries execute individually.

---

## NL Execution Steps

1. Verify HMAC signature: `HMAC-SHA256(secret, profile + ":" + canonical_nl)`. Reject with `403` on failure.
2. Check cache: `(profile, canonical_nl)` → `(sql, parameters)`.
3. On cache miss: delegate to `NlSqlGenerator` with profile schema → `{ sql, parameters }`.
4. Validate SQL via `SqlSafetyValidator` (SELECT-only, known tables).
5. Store in cache. Execute via existing pipeline. Format as normal.

---

## MCP Tools

### `get_schema(profile)`
Returns entities, their SQL table names, base conditions (e.g. `result.type = 'publication'`),
per-field SQL table + column, and join paths between entities. This gives the agent everything
it needs to describe a query without constructing SQL itself.

### `get_field_values(profile, field, limit)`
Returns up to `limit` distinct sample values for a field. Helps the agent understand categorical
fields (e.g. `result.type` → `["publication", "dataset", "software"]`).

### `validate_sql(profile, sql)`
Checks the SQL is a safe SELECT referencing only known profile tables (including join intermediary
tables). Returns `"OK"` or an error message.

### `sign_nl_query(profile, canonical_nl)`
Generates `HMAC-SHA256(secret, profile + ":" + canonical_nl)` and stores the result in a
thread-local (`NlMcpTools.pendingSign`). Returns the constructed chart URL as confirmation text.
`ClaudeNlQueryAgent` reads the thread-local after `chatClient.call()` returns to detect completion.

---

## LLM Abstraction

Two interfaces isolate all LLM-specific code:

**`NlQueryAgent`** — drives the multi-turn conversation and produces a canonical NL description.
```java
public interface NlQueryAgent {
    AgentReply chat(String sessionId, String userMessage, String profile);
}
```
`AgentReply` carries `{ String reply, boolean done, String canonicalNl, String sig }`.

**`NlSqlGenerator`** — translates a canonical NL string into a SQL prepared statement.
```java
public interface NlSqlGenerator {
    SqlResult generate(String canonicalNl, String profile, ProfileSchema schema);
}
```
`SqlResult` carries `{ String sql, List<Object> parameters }`.

The initial implementation of both interfaces uses Claude (Haiku for conversation, Sonnet for
SQL generation). Alternative implementations can be substituted via Spring's `@Primary` /
configuration properties without touching the rest of the codebase.

### Model configuration

```yaml
spring:
  ai:
    anthropic:
      chat:
        options:
          model: claude-sonnet-4-6   # used by NlSqlGenerator

nl:
  agent-model: claude-haiku-4-5-20251001  # used by NlQueryAgent (conversation)
```

### LLM Output Contract

Implementations of `NlSqlGenerator` must return output conforming to:

```json
{
  "sql": "SELECT ... FROM ... WHERE ... GROUP BY ...",
  "parameters": ["value1", "value2"]
}
```

Parameters are ordered to match `?` placeholders in the SQL.

---

## SQL Safety Validation

Before any LLM-generated SQL is cached or executed:

1. Parse the statement — reject anything that is not a single `SELECT`.
2. Extract referenced table names — reject any table not present in the profile configuration
   (entity SQL tables **and** join intermediary tables are both allowed).
3. Reject statements containing DDL keywords (`DROP`, `ALTER`, `CREATE`, `INSERT`, `UPDATE`,
   `DELETE`, `TRUNCATE`).

---

## Caching

Cache key: `(profile, canonical_nl)` → `{ sql, parameters }`.

Stored in the existing cache service, which already has Redis and HSQLDB implementations. This
is a separate cache namespace from the result cache (which keys on `(sql, parameters)` → rows).

---

## HMAC Signing

```
signature = HMAC-SHA256(signing_secret, profile + ":" + canonical_nl)
```

`signing_secret` is a configurable property in `application.yml`:

```yaml
nl:
  signing-secret: change-me-in-production
  session-ttl-minutes: 30
  session-cleanup-interval-ms: 60000
  base-url: /chart/json
  agent-model: claude-haiku-4-5-20251001
```

NL queries with an invalid or missing signature are rejected with `403 Forbidden`. Requests
using the JSON DSL or named queries are unaffected.

---

## Profile Schema Exposed to the Agent

`getSchema()` returns a `ProfileSchema` that includes, per entity:

- **`sqlTable`** — actual SQL table name (may differ from entity name, e.g. `publication` → `result`)
- **`baseConditions`** — mandatory WHERE conditions pre-applied to the entity (e.g. `result.type = 'publication'`), which the SQL generator must always include
- **`fields`** — each with logical name, datatype, actual `sqlTable`, and actual `column`
- **`joinPaths`** — join intermediary tables (e.g. `result → result_projects → project`)

This allows the LLM to generate correct SQL without guessing table or column names.

---

## Module Placement

| Concern | Module |
|---|---|
| `NlQueryAgent` + `NlSqlGenerator` interfaces | `ChartDataFormatter` (`nl` package) |
| Claude implementation of both interfaces | `ChartDataFormatter` (`nl.claude` package) |
| MCP tools | `ChartDataFormatter` (`nl.mcp` package) |
| `/nl/chat` endpoint + session management | `ChartDataFormatter` (`nl.conversation` package) |
| HMAC signing utility | `ChartDataFormatter` (`nl.signing` package) |
| Per-chart dispatch in existing chart endpoint | `ChartDataFormatter` (`Handlers.RequestBodyHandler`) |
| `nl` / `sig` fields on `Query` | `DBAccess` (`domain.Query`) |
| SQL safety validation | `DBAccess` (`mapping.SqlSafetyValidator`) |

---

## Out of Scope (this phase)

- Profile description auto-generation.
- Persistent conversation sessions.
- Per-user rate limiting.
- Streaming responses from the agent.
