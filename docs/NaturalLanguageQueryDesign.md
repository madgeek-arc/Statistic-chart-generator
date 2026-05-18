# Natural Language Query — Design Document

## Overview

Users describe their data and chart appearance in plain English. LLM-driven agents refine each description through conversation, then produce signed canonical strings. The frontend embeds these signed strings inside a normal chart request. The backend verifies signatures, generates SQL / chart options (with caching), and returns chart data through the existing pipeline.

The JSON DSL and named queries remain fully supported with no breaking changes.

---

## Goals

- Allow users to describe data queries in natural language instead of the JSON DSL.
- Allow users to describe chart appearance (colours, titles, axes, …) in natural language.
- Cache LLM-generated SQL and chart options to avoid redundant API calls.
- Prevent abuse of LLM API tokens via HMAC-signed canonical strings (applied to both SQL and options).
- Keep LLM integration behind interfaces so implementations (Claude, other models) are swappable.
- Expose reusable MCP tools so other portals can build their own agentic UX on top of the same backend.

---

## Architecture

### NL → SQL

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
│  MCP Tools (NlMcpTools)                             │
│  ├── get_schema(profile)                            │
│  ├── get_field_values(profile, field, limit)        │
│  ├── validate_sql(profile, sql)                     │
│  └── sign_nl_query(profile, canonical_nl)  ◄─ done │
└─────────────────────────────────────────────────────┘

  ── conversation done → frontend POSTs /chart with query.nl+sig ──►

┌─────────────────────────────────────────────────────┐
│  RequestBodyHandler  →  NlQueryService              │
│  Verify HMAC → cache lookup → NlSqlGenerator        │
│  → SqlSafetyValidator → execute → format chart      │
└─────────────────────────────────────────────────────┘
```

### NL → Chart Options

```
┌─────────────────────────────────────────────────────┐
│  Angular UI (or other portal)                       │
│  POST /nl/options/chat  ──────────────────────────► │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  NlOptionsChatController → NlOptionsChatAgent       │
│  Manages in-memory session (same store/TTL)         │
│  Drives iterative appearance-design conversation    │
└─────────────────────────────────────────────────────┘
                        │  tool calls
                        ▼
┌─────────────────────────────────────────────────────┐
│  MCP Tools (NlOptionsMcpTools)                      │
│  ├── preview_options(optionsJson)                   │
│  └── sign_chart_options(library, desc, optionsJson) │
└─────────────────────────────────────────────────────┘

  ── done → frontend POSTs /chart with nlOptions+optionsSig ──►

┌─────────────────────────────────────────────────────┐
│  RequestBodyHandler  →  NlOptionsService            │
│  Verify HMAC → cache lookup → NlOptionsGenerator    │
│  → chartOptions: JsonNode attached to response      │
└─────────────────────────────────────────────────────┘
```

Other portals connect directly to the MCP server and manage their own conversation loop.

---

## Conversation Flow (Angular UI)

### NL → SQL

1. User sends a natural language message to `POST /nl/chat`.
2. Backend delegates to `NlQueryAgent`, which calls `get_schema` or `get_field_values` (via MCP tools) to ground its responses in the actual profile.
3. Agent replies with clarifications or suggestions. Backend returns `{ reply, sessionId, done: false }`.
4. Conversation continues until the agent is satisfied with the NL description.
5. Agent calls `sign_nl_query(profile, canonical_nl)` internally — detected via a thread-local flag.
6. Backend returns `{ reply, sessionId, done: true, canonicalNl: "...", sig: "<hmac>" }`.
7. Session is discarded (in-memory, no persistence).

### NL → Chart Options

1. User sends a chart appearance description to `POST /nl/options/chat`.
2. Backend delegates to `NlOptionsChatAgent`, which iteratively refines chart options JSON for the target library.
3. Agent calls `preview_options(optionsJson)` to show the user what the options look like.
4. When the user confirms, agent calls `sign_chart_options(library, canonicalDescription, optionsJson)`.
5. Backend returns `{ reply, sessionId, done: true, canonicalDescription, sig, optionsJson }`.
6. Frontend includes `nlOptions` + `optionsSig` in the subsequent `/chart` POST.
7. Backend verifies the signature, looks up the options in cache (or regenerates), and attaches them as `chartOptions` in the chart response.

Session state is held in-memory per JVM instance. A configurable TTL evicts idle sessions.

---

## Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/nl/chat` | POST | Multi-turn conversation to produce a signed NL query |
| `/nl/options/chat` | POST | Multi-turn conversation to design signed chart options |
| `/chart` (POST) | POST | Chart data; supports `nlOptions`+`optionsSig` for inline options |
| `/raw` (POST/GET) | POST/GET | Raw data; NL query supported in `query{}` |
| `/table` (POST) | POST | Table data; NL query supported in `query{}` |

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

Once the NL chat conversation is done, the frontend constructs a standard `POST /chart` body.
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

### NL chart options

To attach AI-generated chart options, add `nlOptions` and `optionsSig` at the top level of the chart request alongside the existing `chartsInfo`:

```json
{
  "library": "HighCharts",
  "chartsInfo": [{ "type": "bar", "query": { "nl": "...", "sig": "...", "profile": "..." } }],
  "nlOptions": "blue bars, red title, legend on the right",
  "optionsSig": "<hmac>"
}
```

The backend verifies the signature, looks up (or regenerates) the options JSON, and attaches it as `chartOptions` in the response:

```json
{
  "series": [...],
  "chartOptions": {
    "colors": ["#003399"],
    "title": { "text": "Publications per year", "style": { "color": "red" } },
    "legend": { "align": "right", "layout": "vertical" }
  }
}
```

### Dispatch priority in `RequestBodyHandler`

`RequestBodyHandler` iterates over `chartsInfo` and dispatches each chart independently:

1. `query.nl` is non-blank → **NL path** (verify sig, cache lookup, SQL generation)
2. `query.name` is non-blank → **named query** path
3. Otherwise → **JSON DSL** path

DSL and named queries are still batched together into a single SQL statement (CTE merging preserved). NL queries execute individually.

After chart data is collected and formatted, if `nlOptions` + `optionsSig` are both present:
4. Verify options HMAC → cache lookup → `NlOptionsGenerator` (regenerate if miss) → attach `chartOptions`.

---

## NL Query Execution Steps

1. Verify HMAC signature: `HMAC-SHA256(secret, profile + ":" + canonical_nl)`. Reject with `403` on failure.
2. Check cache: `(profile, canonical_nl, schemaFingerprint)` → `(sql, parameters)`.
3. On cache miss: delegate to `NlSqlGenerator` with profile schema → `{ sql, parameters }`.
4. Validate SQL via `SqlSafetyValidator` (SELECT-only, known tables).
5. Store in cache. Execute via existing pipeline. Format as normal.

---

## NL Options Execution Steps

1. Verify HMAC signature: `HMAC-SHA256(secret, library + ":" + canonical_description)`. Reject with `403` on failure.
2. Check cache: `(library, canonical_description, promptVersion)` → `optionsJson`.
3. On cache miss: delegate to `NlOptionsGenerator` (single-shot LLM call) → `optionsJson`.
4. Store in cache. Parse as `JsonNode`. Attach as `chartOptions` on the response.

---

## MCP Tools

### SQL tools (`NlMcpTools`)

#### `get_schema(profile)`
Returns entities, their SQL table names, base conditions (e.g. `result.type = 'publication'`),
per-field SQL table + column, and join paths between entities. This gives the agent everything
it needs to describe a query without constructing SQL itself.

#### `get_field_values(profile, field, limit)`
Returns up to `limit` distinct sample values for a field. Helps the agent understand categorical
fields (e.g. `result.type` → `["publication", "dataset", "software"]`).

#### `validate_sql(profile, sql)`
Checks the SQL is a safe SELECT referencing only known profile tables (including join intermediary
tables). Returns `"OK"` or an error message.

#### `sign_nl_query(profile, canonical_nl)`
Generates `HMAC-SHA256(secret, profile + ":" + canonical_nl)`, caches the generated SQL, and stores
the result in a thread-local (`NlMcpTools.pendingSign`). Returns the constructed chart URL as
confirmation text. `ClaudeNlQueryAgent` reads the thread-local after `chatClient.call()` returns
to detect completion.

### Options tools (`NlOptionsMcpTools`)

#### `preview_options(optionsJson)`
Validates the options JSON structure and returns it pretty-printed for the user to review. Returns
an `"INVALID JSON: ..."` error string if the JSON is malformed.

#### `sign_chart_options(library, canonicalDescription, optionsJson)`
Validates the JSON, caches it under `(library, canonicalDescription, promptVersion)`, generates
`HMAC-SHA256(secret, library + ":" + canonicalDescription)`, and stores the result in a thread-local
(`NlOptionsMcpTools.pendingSign`). Returns `"Chart options signed successfully."`.
`ClaudeNlOptionsChatAgent` reads the thread-local after `chatClient.call()` returns to detect
completion.

---

## LLM Abstraction

Four interfaces isolate all LLM-specific code:

**`NlQueryAgent`** — drives the multi-turn SQL conversation.
```java
public interface NlQueryAgent {
    AgentReply chat(String sessionId, String userMessage, String profile);
}
```
`AgentReply` carries `{ String reply, boolean done, String canonicalNl, String sig, String sql }`.

**`NlSqlGenerator`** — translates a canonical NL string into a SQL prepared statement.
```java
public interface NlSqlGenerator {
    SqlResult generate(String canonicalNl, String profile, ProfileSchema schema);
}
```
`SqlResult` carries `{ String sql, List<Object> parameters }`.

**`NlOptionsChatAgent`** — drives the multi-turn chart options conversation.
```java
public interface NlOptionsChatAgent {
    OptionsAgentReply chat(String sessionId, String userMessage, String library);
}
```
`OptionsAgentReply` carries `{ String reply, boolean done, String canonicalDescription, String sig, String optionsJson }`.

**`NlOptionsGenerator`** — produces options JSON from a canonical description in one LLM call.
```java
public interface NlOptionsGenerator {
    String generate(String library, String canonicalDescription);
}
```

The initial implementations use Claude (Haiku for conversation, Sonnet for SQL and options generation). Alternative implementations can be substituted via Spring's `@Primary` / configuration properties without touching the rest of the codebase.

### Model configuration

```yaml
spring:
  ai:
    anthropic:
      chat:
        options:
          model: claude-sonnet-4-6   # used by NlSqlGenerator and NlOptionsGenerator

nl:
  agent-model: claude-haiku-4-5-20251001  # used by NlQueryAgent and NlOptionsChatAgent (conversation)
```

### LLM Output Contract

Implementations of `NlSqlGenerator` must return output conforming to:

```json
{
  "sql": "SELECT ... FROM ... WHERE ... GROUP BY ...",
  "parameters": ["value1", "value2"]
}
```

Implementations of `NlOptionsGenerator` must return a valid JSON object that the target charting library can consume directly as options/config (e.g. HighCharts `Highcharts.chart(..., options)`).

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

### NL SQL cache

Cache key: `(profile, canonical_nl, schemaFingerprint)` → `{ sql, parameters }`.

The `schemaFingerprint` is an MD5 of all `entity → sqlTable` and `field → sqlTable.column` mappings from the profile configuration, sorted lexicographically. If the schema changes (new fields, renamed tables), all cached queries for that profile are automatically invalidated — they become cache misses and are regenerated.

Backed by HSQLDB (`DbNlSqlCache`, `@Primary`) with an in-memory fallback (`InMemoryNlSqlCache`).

### NL options cache

Cache key: `(library, canonical_description, promptVersion)` → `optionsJson`.

The `promptVersion` is a config property (`nl.options-prompt-version`). Bumping it in `application.yml` invalidates all cached options entries — they will be regenerated on next request. Use this after changing the system prompt for the options agent.

Backed by HSQLDB (`DbNlOptionsCache`, `@Primary`) with an in-memory fallback (`InMemoryNlOptionsCache`).

---

## HMAC Signing

Both NL query and NL options use the same signing scheme:

```
# NL query
signature = HMAC-SHA256(signing_secret, profile + ":" + canonical_nl)

# NL options
signature = HMAC-SHA256(signing_secret, library + ":" + canonical_description)
```

The purpose of signing is to **gate LLM access**: unsigned NL strings never reach Claude, neither for SQL generation nor for options generation. A signature is only issued after the user has gone through a supervised conversation and confirmed the description.

`signing_secret` is a configurable property in `application.yml`:

```yaml
nl:
  signing-secret: change-me-in-production
  session-ttl-minutes: 30
  session-cleanup-interval-ms: 60000
  base-url: /chart/json
  agent-model: claude-haiku-4-5-20251001
  options-prompt-version: "1"
```

NL queries or options with an invalid or missing signature are rejected with `403 Forbidden`. Requests using the JSON DSL or named queries are unaffected.

---

## Profile Schema Exposed to the Agent

`getSchema()` returns a `ProfileSchema` that includes, per entity:

- **`sqlTable`** — actual SQL table name (may differ from entity name, e.g. `publication` → `result`)
- **`baseConditions`** — mandatory WHERE conditions pre-applied to the entity (e.g. `result.type = 'publication'`), which the SQL generator must always include
- **`fields`** — each with logical name, datatype, actual `sqlTable`, and actual `column`
- **`joinPaths`** — join intermediary tables (e.g. `result → result_projects → project`)

This allows the LLM to generate correct SQL without guessing table or column names.

Schema building is centralised in `ProfileSchemaBuilder` (a `@Component` in `ChartDataFormatter`).
Both `NlQueryService` and `NlMcpTools` delegate to it, ensuring consistent schema representation
whether the schema is used internally or exposed via the MCP server.

---

## Module Placement

| Concern | Module |
|---------|--------|
| `NlQueryAgent` + `NlSqlGenerator` interfaces | `ChartDataFormatter` (`nl` package) |
| `NlOptionsChatAgent` + `NlOptionsGenerator` interfaces | `ChartDataFormatter` (`nl.options` package) |
| Claude implementation of SQL interfaces | `ChartDataFormatter` (`nl.claude` package) |
| Claude implementation of options interfaces | `ChartDataFormatter` (`nl.options.claude` package) |
| SQL MCP tools | `ChartDataFormatter` (`nl.mcp` package) |
| Options MCP tools | `ChartDataFormatter` (`nl.options.mcp` package) |
| `/nl/chat` endpoint + session management | `ChartDataFormatter` (`RestControllers`, `nl.conversation` package) |
| `/nl/options/chat` endpoint | `ChartDataFormatter` (`RestControllers` package) |
| HMAC signing utility | `ChartDataFormatter` (`nl.signing` package) |
| `ProfileSchemaBuilder` (shared schema building) | `ChartDataFormatter` (`nl` package) |
| `NlQueryService` (verify + cache + execute) | `ChartDataFormatter` (`nl` package) |
| `NlOptionsService` (verify + cache + attach) | `ChartDataFormatter` (`nl.options` package) |
| Per-chart dispatch + options attachment | `ChartDataFormatter` (`Handlers.RequestBodyHandler`) |
| `nl` / `sig` fields on `Query` | `DBAccess` (`domain.Query`) |
| SQL safety validation | `DBAccess` (`mapping.SqlSafetyValidator`) |
| `NlSqlCache` interface + HSQLDB/in-memory impls | `DBAccess` (`repositories` package) |
| `NlOptionsCache` interface + HSQLDB/in-memory impls | `DBAccess` (`repositories` package) |
| Cache management endpoints | `DBAccess` (`controllers.CacheController`) |

---

## Out of Scope (this phase)

- Profile description auto-generation.
- Persistent conversation sessions.
- Per-user rate limiting.
- Streaming responses from the agent.
