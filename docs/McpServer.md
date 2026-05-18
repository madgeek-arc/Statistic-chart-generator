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

## SQL Tools

These tools are used by the NL → SQL conversation agent (`NlMcpTools`). They allow an external LLM to build and sign a data query.

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

Signs the canonical NL description and returns a chart URL. Call this only when the user has confirmed the query is correct — it is the terminal step of the SQL conversation.

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

## Chart Options Tools

These tools are used by the NL → chart options conversation agent (`NlOptionsMcpTools`). They allow an external LLM to iteratively design and sign chart appearance options for the target charting library.

### `preview_options(optionsJson)`

Validates the options JSON structure and returns it pretty-printed for the user to review.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `optionsJson` | string | A JSON object containing chart options for the target library |

**Returns:** pretty-printed JSON string, or `"INVALID JSON: <reason>"` if the input cannot be parsed.

Use this during the options conversation to show the user what options have been built so far before finalising.

---

### `sign_chart_options(library, canonicalDescription, optionsJson)`

Validates, caches, and signs the final chart options. Call this only when the user is satisfied with the chart appearance — it is the terminal step of the options conversation.

**Arguments:**

| Name | Type | Description |
|------|------|-------------|
| `library` | string | Target charting library: `"HighCharts"`, `"eCharts"`, or `"GoogleCharts"` |
| `canonicalDescription` | string | Concise English description of the appearance (e.g. "blue bars, red title, legend on the right") |
| `optionsJson` | string | Final chart options JSON object |

**Returns:** `"Chart options signed successfully."` or `"ERROR: Invalid JSON: <reason>"`

The signature is `HMAC-SHA256(signing_secret, library + ":" + canonicalDescription)`.

Once signed, the frontend passes `canonicalDescription` as `nlOptions` and the signature as `optionsSig` in the `/chart` POST body. The backend verifies the signature, looks up the options JSON in the cache, and attaches it as `chartOptions` in the response.

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

## Suggested agent workflows

### NL → SQL workflow

```
1. call get_profiles()              → pick or confirm the profile
2. call get_schema(profile)         → understand entities, fields, joins
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

### NL → chart options workflow

```
1. Determine the target library from context (HighCharts, eCharts, …)
2. Iteratively build an options JSON object based on user feedback
3. call preview_options(optionsJson)
                                    → show the user the current options
4. Adjust options based on user feedback; repeat from step 3 if needed
5. call sign_chart_options(library, canonicalDescription, optionsJson)
                                    → finalise; cache and sign the options
```

The frontend then includes in its next `/chart` POST:
```json
{
  "library": "HighCharts",
  "chartsInfo": [{ ... }],
  "nlOptions": "<canonicalDescription>",
  "optionsSig": "<sig>"
}
```

The backend attaches `chartOptions: { ... }` to the chart response, which the portal uses to configure the charting library.

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
  signing-secret: change-me-in-production   # HMAC key for both sign_nl_query and sign_chart_options
  base-url: /chart/json                     # prefix prepended to signed URLs
  options-prompt-version: "1"               # bump to invalidate all cached chart options
```

Change `signing-secret` in production. Any NL query or options signed with the old secret will be rejected after rotation. Bumping `options-prompt-version` forces all chart options to be regenerated on next use (useful after updating the options system prompt).
