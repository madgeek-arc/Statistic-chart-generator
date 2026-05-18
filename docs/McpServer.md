# MCP Server

The application embeds a Spring AI MCP server that exposes the same data-access tools used internally by the NL query agent. External portals and LLM-based tools can connect to it to build their own agentic UX without going through the `/nl/chat` endpoint.

---

## Transport

The server uses **SSE (Server-Sent Events)** transport in SYNC mode. It is served on the same host and port as the rest of the application — no separate process or port.

| Property | Value |
|----------|-------|
| SSE endpoint | `http://<host>:<port>/sse` |
| Messages endpoint | `http://<host>:<port>/mcp/messages` |
| Protocol | MCP over SSE (JSON-RPC) |
| Mode | SYNC (tool calls execute synchronously on the server) |

---

## Tools

### `get_profiles()`

Returns the list of available data profiles.

**Arguments:** none

**Returns:** array of objects
```json
[
  { "name": "openaire_stats", "description": "OpenAIRE research statistics" },
  ...
]
```

Use this to let users choose a profile before starting a query session.

---

### `get_schema(profile)`

Returns the full schema for a profile: entities, their SQL table names, mandatory base conditions, fields with actual SQL table/column, and join paths between entities.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `profile` | string | Profile name (from `get_profiles`) |

**Returns:** `ProfileSchema` object
```json
{
  "profile": "openaire_stats",
  "entities": [
    {
      "name": "publication",
      "description": "publication",
      "sqlTable": "result",
      "baseConditions": ["result.type = 'publication'"],
      "fields": [
        {
          "name": "year",
          "datatype": "int",
          "description": "year",
          "sqlTable": "result",
          "column": "year"
        },
        {
          "name": "funder",
          "datatype": "text",
          "description": "funder",
          "sqlTable": "project",
          "column": "funder"
        }
      ],
      "joinPaths": [
        "join project via: result.id = result_projects.id → result_projects.project_id = project.id"
      ]
    }
  ]
}
```

**Important fields:**
- `sqlTable` — the actual SQL table name, which may differ from the entity name (e.g. `publication` → `result`)
- `baseConditions` — WHERE clauses that must always be included when querying this entity
- `fields[].sqlTable` / `fields[].column` — actual SQL location of each field, which may be in a joined table
- `joinPaths` — intermediate tables needed to join entities

---

### `get_field_values(profile, field, limit)`

Returns up to `limit` distinct sample values for a field. Useful for understanding categorical fields before filtering.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `profile` | string | Profile name |
| `field` | string | Field reference in `entity.fieldName` format (e.g. `publication.type`) |
| `limit` | int | Maximum number of values to return |

**Returns:** array of strings
```json
["open access", "closed", "embargo", "restricted"]
```

Returns an empty array if the field has no data or an error occurs.

---

### `validate_sql(profile, sql)`

Checks that a SQL statement is safe to execute: must be a single `SELECT`, may only reference tables known to the profile (entity tables and join intermediary tables), and must not contain DDL keywords.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `profile` | string | Profile name |
| `sql` | string | SQL statement to validate |

**Returns:** `"OK"` or `"INVALID: <reason>"`

---

### `sign_nl_query(profile, canonicalNl)`

Signs the canonical NL description and returns a chart URL. Call this only when the user has confirmed the query is correct — it is the terminal step of the conversation.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `profile` | string | Profile name |
| `canonicalNl` | string | Concise English description of the query (e.g. "Number of open access publications per year in Greece") |

**Returns:** signed chart URL (string)
```
/chart/json?profile=openaire_stats&nl=Number+of+open+access...&sig=<hmac>
```

The signature is `HMAC-SHA256(signing_secret, profile + ":" + canonicalNl)`. The URL can be embedded directly in a portal page or passed to the chart data endpoints as `query.nl` + `query.sig`.

---

## Connecting from an external client

Any MCP-compatible client (Claude Desktop, custom LLM agent, etc.) can connect using the SSE transport URL.

**Example — Claude Desktop `claude_desktop_config.json`:**
```json
{
  "mcpServers": {
    "statistic-chart-generator": {
      "url": "http://localhost:8090/stats-api/sse"
    }
  }
}
```

**Example — programmatic client (Python `mcp` SDK):**
```python
from mcp.client.sse import sse_client

async with sse_client("http://localhost:8090/stats-api/sse") as (read, write):
    # list tools, call get_profiles, etc.
```

---

## Suggested agent workflow

```
1. call get_profiles()          → pick or confirm the profile
2. call get_schema(profile)     → understand entities, fields, joins
3. (optional) call get_field_values(profile, field, limit)
                                → explore categorical field values
4. (optional) call validate_sql(profile, sql)
                                → sanity-check a generated SQL fragment
5. call sign_nl_query(profile, canonicalNl)
                                → finalise; get the signed chart URL
```

The signed URL returned in step 5 can be resolved by any of the chart data endpoints:
- `GET /chart/json?json={"library":"HighCharts","chartsInfo":[{"type":"bar","query":{"nl":"...","sig":"...","profile":"..."}}]}`
- `POST /chart` with the same structure as a JSON body
- `GET /raw?json=...` / `POST /table` — same `query` structure

---

## Configuration

```yaml
spring:
  ai:
    mcp:
      server:
        name: statistic-chart-generator
        version: 0.0.2
        type: SYNC   # tool calls execute on the calling thread

nl:
  signing-secret: change-me-in-production   # HMAC key for sign_nl_query
  base-url: /chart/json                     # prefix prepended to signed URLs
```

Change `signing-secret` in production. Any NL query signed with the old secret will be rejected after rotation.
