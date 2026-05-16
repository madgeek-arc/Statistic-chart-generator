# Natural Language Query — Design Document

## Overview

Users describe their data in plain English. An LLM-driven agent refines the description through conversation, then generates a signed URL embedding the canonical natural language query. When the URL is resolved, the NL is translated directly to a SQL prepared statement and executed through the existing pipeline.

The JSON DSL remains fully supported (no breaking changes).

---

## Goals

- Allow users to describe data queries in natural language instead of the JSON DSL.
- Cache LLM-generated SQL to avoid redundant API calls.
- Prevent abuse of LLM API tokens via HMAC-signed URLs for NL queries.
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
│  Java backend — NlQueryAgent (interface)            │
│  Manages in-memory session (configurable TTL)       │
│  Drives agent loop via pluggable LLM backend        │
└─────────────────────────────────────────────────────┘
                        │  tool calls
                        ▼
┌─────────────────────────────────────────────────────┐
│  MCP Server (same application)                      │
│  ├── get_schema(profile)                            │
│  ├── get_field_values(profile, field, limit)        │
│  ├── validate_sql(profile, sql, parameters)         │
│  └── sign_nl_query(profile, canonical_nl)           │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  Existing pipeline                                  │
│  Cache → SQL execution → Chart formatting           │
└─────────────────────────────────────────────────────┘
```

Other portals connect directly to the MCP server and manage their own conversation loop.

---

## Conversation Flow (Angular UI)

1. User sends a natural language message to `POST /nl/chat`.
2. Backend delegates to `NlQueryAgent`, which may call `get_schema` or `get_field_values` (via MCP tools) to ground its responses in the actual profile.
3. Agent replies with clarifications or suggestions. Backend returns `{ reply, sessionId, done: false }`.
4. Conversation continues until the agent is satisfied with the NL description.
5. Agent calls `sign_nl_query(profile, canonical_nl)` internally.
6. Backend returns `{ reply, sessionId, done: true, url: "..." }`.
7. Session is discarded (in-memory, no persistence).

Session state is held in-memory per JVM instance. A configurable TTL evicts idle sessions.

---

## URL Execution Flow

Existing chart endpoints detect an `nl=` parameter alongside the current `query=`:

```
GET /chart?type=bar&profile=openaire&nl=<canonical_nl>&sig=<hmac>
```

Steps:
1. Verify HMAC signature: `HMAC-SHA256(secret, profile + ":" + canonical_nl)`.
2. Check cache: `(profile, canonical_nl)` → `(sql, parameters)`.
3. On cache miss: delegate to `NlSqlGenerator` with profile schema + descriptions → `{ sql, parameters }`.
4. Validate SQL (see SQL Safety).
5. Store in cache. Execute. Format as normal.

The `query=` (JSON DSL) path is unchanged. `sig=` is required only when `nl=` is present.

---

## MCP Tools

### `get_schema(profile)`
Returns tables, fields, relations, and their descriptions from the profile configuration.
Descriptions fall back to the entity/column name if not set.

### `get_field_values(profile, field, limit)`
Returns up to `limit` distinct sample values for a field. Helps the agent understand categorical fields (e.g. `result.type` → `["publication", "dataset", "software"]`).

### `validate_sql(profile, sql, parameters)`
Runs `EXPLAIN` (or equivalent) against the target datasource to check the SQL is syntactically valid before finalizing.

### `sign_nl_query(profile, canonical_nl)`
Generates `HMAC-SHA256(secret, profile + ":" + canonical_nl)` and returns the full signed URL. Called by the agent when the conversation is complete.

---

## LLM Abstraction

Two interfaces isolate all LLM-specific code:

**`NlQueryAgent`** — drives the multi-turn conversation and produces a canonical NL description.
```java
public interface NlQueryAgent {
    AgentReply chat(String sessionId, String userMessage, String profile);
}
```
`AgentReply` carries `{ String reply, boolean done, String canonicalNl }`.

**`NlSqlGenerator`** — translates a canonical NL string into a SQL prepared statement.
```java
public interface NlSqlGenerator {
    SqlResult generate(String canonicalNl, String profile, ProfileSchema schema);
}
```
`SqlResult` carries `{ String sql, List<Object> parameters }`, matching the contract of `SqlQueryTree.makeQuery()`.

The initial implementation of both interfaces uses Claude. Alternative implementations (other LLMs, local models) can be substituted via Spring's `@Primary` / configuration properties without touching the rest of the codebase.

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
2. Extract referenced table names — reject any table not present in the profile configuration.
3. Reject statements containing DDL keywords (`DROP`, `ALTER`, `CREATE`, `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`).

---

## Caching

Cache key: `(profile, canonical_nl)` → `{ sql, parameters }`.

Stored in the existing cache service, which already has Redis and HSQLDB implementations. This is a separate cache namespace from the result cache (which keys on `(sql, parameters)` → rows).

---

## HMAC Signing

```
signature = HMAC-SHA256(signing_secret, profile + ":" + canonical_nl)
```

`signing_secret` is a configurable property in `application.yml`:

```yaml
nl:
  signing-secret: <secret>
  session-ttl-minutes: 30
```

Unsigned requests with `nl=` are rejected with `403 Forbidden`. Requests with `query=` (JSON DSL) are unaffected.

---

## Profile Description Extension

`Table` and `Field` entities in the profile configuration gain an optional `description` field:

```json
{
  "entities": [
    {
      "name": "publication",
      "from": "result",
      "key": "id",
      "description": "Peer-reviewed journal articles and conference papers",
      "fields": [
        { "column": "year",        "name": "year",        "datatype": "int" },
        { "column": "bestlicense", "name": "access mode", "datatype": "text",
          "description": "Open access licence of the publication" }
      ]
    }
  ]
}
```

`description` is optional on both entities and fields. Default when absent: the entity/field `name` value. Descriptions are included in the `get_schema` tool response and injected into the Claude prompt for SQL generation.

Classes that need updating: `MappingEntity` and `MappingField` (JSON deserialization), `Table` and `Field` (runtime), and `Mapper.buildConfiguration()` which wires them together.

Auto-generation of descriptions is out of scope for this phase and will be addressed separately.

---

## Module Placement

| Concern | Module |
|---|---|
| `NlQueryAgent` + `NlSqlGenerator` interfaces | `ChartDataFormatter` (new package `nl`) |
| Claude implementation of both interfaces | `ChartDataFormatter` (new package `nl.claude`) |
| MCP server + tools | `ChartDataFormatter` (new package `nl.mcp`) |
| `/nl/chat` endpoint + session management | `ChartDataFormatter` (new package `nl.conversation`) |
| HMAC signing utility | `ChartDataFormatter` (new package `nl.signing`) |
| NL cache (sql+params store) | `DBAccess` (extend existing cache service) |
| Profile description fields | `DBAccess` (extend `MappingEntity`, `MappingField`, `Table`, `Field`) |
| SQL safety validation | `DBAccess` (new class `SqlSafetyValidator`) |
| `nl=` detection in existing endpoints | `ChartDataFormatter` (existing controllers) |

---

## Out of Scope (this phase)

- Profile description auto-generation.
- Persistent conversation sessions.
- Per-user rate limiting on the signing endpoint.
- Streaming responses from the agent.
