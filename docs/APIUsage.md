# API Usage

All data endpoints accept JSON. Base path: `http://localhost:8090/stats-api`.

---

## Core Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/chart` | Chart-formatted data (HighCharts / GoogleCharts / ECharts) |
| `GET`  | `/chart/json?json=` | Same as POST but URL-encoded |
| `POST` | `/table` | Tabular data — same request format as `/chart` |
| `GET`  | `/raw?json=` | Raw unformatted rows |
| `POST` | `/chart/shorten` | Shorten a chart URL via TinyURL |
| `POST` | `/nl/chat` | Natural language query (multi-turn) |
| `GET`  | `/nl/info` | Resolve signed NL query to SQL (no LLM call) |
| `POST` | `/nl/sign` | Sign a canonical NL query (trusted backend use) |

Schema discovery: `GET /schema/profiles`, `GET /schema/{profile}/entities`, `GET /schema/{profile}/entities/{entity}`, `GET /schema/{profile}/fields/{field}`.

---

## Request Format

```json
POST /chart
{
  "library":    "HighCharts",
  "orderBy":    null,
  "chartsInfo": [
    {
      "name":  "Publications by year",
      "type":  "bar",
      "color": "#2f7ed8",
      "query": {
        "entity":   "publication",
        "profile":  "openaire",
        "select": [
          { "field": "publication",      "aggregate": "count", "order": 1 },
          { "field": "publication.year",                       "order": 2 }
        ],
        "filters": [
          {
            "groupFilters": [
              { "field": "publication.year",   "type": "between", "values": ["2010","2020"] },
              { "field": "publication.funder", "type": "=",       "values": ["H2020"] }
            ],
            "op": "AND"
          }
        ],
        "limit":    20,
        "useCache": true
      }
    }
  ]
}
```

### `query` fields

| Field | Required | Description |
|-------|----------|-------------|
| `entity` | yes | Logical entity name from the profile (`"publication"`, `"project"`, …) |
| `profile` | no | Profile key. Omit to use the default (primary) profile. |
| `select` | no | Columns to return. First entry = y-axis (aggregate); subsequent = x-axis dimensions. |
| `filters` | no | WHERE conditions. Multiple `FilterGroup` entries combined with AND. |
| `limit` | no | Max rows. `0` = unlimited. Default 70. |
| `useCache` | no | Use result cache. Default `true`. |

### `select` entry

| Field | Description |
|-------|-------------|
| `field` | Dot path: `"entity"`, `"entity.field"`, or `"entity.relation.field"` |
| `aggregate` | `"count"` / `"sum"` / `"avg"` / `"min"` / `"max"`. Omit for GROUP BY dimension. |
| `order` | Position in output (1 = y, 2+ = x dimensions) |

### Filter operators

| `type` | SQL | Notes |
|--------|-----|-------|
| `"="` | `col = ?` or `IN (…)` | Multiple values → `IN` |
| `"!="` | `col != ?` or `NOT IN (…)` | Multiple values → `NOT IN` |
| `"in"` / `"not_in"` | `IN` / `NOT IN` | Always list form |
| `">"`, `">="`, `"<"`, `"<="` | comparison | — |
| `"between"` | `BETWEEN ? AND ?` | Exactly 2 values |
| `"contains"` | `LIKE %…%` | — |
| `"startsWith"` | `LIKE …%` | — |
| `"is_null"` / `"is_not_null"` | `IS NULL` / `IS NOT NULL` | No values needed |

### `orderBy` (multi-query only)

| Value | Effect |
|-------|--------|
| `null` (default) | x-axis = union of all queries, ordered alphabetically |
| `"xaxis"` | x-axis driven by q1, LEFT JOIN others (time-series alignment) |
| `"stacked"` | Same as null but sorted by combined value DESC |
| `"pinned"` | q1's x-values always appear first, rest sorted by value DESC |
| `"yaxis"` | q1 defines the top-N; q2…qN add series for same x-values |

---

## Responses

### HighCharts / ECharts

```json
{
  "series":           [{ "data": [150, 200, 240] }],
  "xAxis_categories": ["2010", "2011", "2012"],
  "dataSeriesNames":  ["Publications by year"],
  "dataSeriesTypes":  ["bar"]
}
```

Missing x-values filled with `null`.

### GoogleCharts

```json
{
  "dataTable":   [[2010, 150], [2011, 200]],
  "columns":     ["Year", "Publications by year"],
  "columnsType": ["string", "number"]
}
```

### Raw (`/raw`)

```json
{ "data": [[[2010, 150], [2011, 200]]] }
```

`data[i]` = rows for series `i`. Add `"verbose": true` for labelled output with original query objects.

---

## Multi-Series Example

```json
POST /chart
{
  "library": "HighCharts",
  "orderBy": "xaxis",
  "chartsInfo": [
    { "name": "H2020", "query": { "entity": "publication", "profile": "openaire",
        "select": [{ "field": "publication", "aggregate": "count" },
                   { "field": "publication.year" }],
        "filters": [{ "groupFilters": [{ "field": "publication.funder", "type": "=", "values": ["H2020"] }], "op": "AND" }] }},
    { "name": "FP7",   "query": { "entity": "publication", "profile": "openaire",
        "select": [{ "field": "publication", "aggregate": "count" },
                   { "field": "publication.year" }],
        "filters": [{ "groupFilters": [{ "field": "publication.funder", "type": "=", "values": ["FP7"] }], "op": "AND" }] }}
  ]
}
```

---

## NL Query Flow

```
POST /nl/chat { profile, message }
  ↓  repeat until done: true
  { sessionId, reply, done }  →  when done: { canonicalNl, sig, sql }
  ↓
POST /chart {
  library: "HighCharts",
  chartsInfo: [{ type: "bar", query: { nl: <canonicalNl>, sig: <sig>, profile: <profile> } }]
}
```

Each turn must echo `sessionId`. On completion, use `canonicalNl` + `sig` as the `query` object. Additional filters added after signing require re-signing via `POST /nl/sign` (trusted backend, `X-Sign-Key` header).

---

## CORS

All endpoints allow `*` origins. No proxy needed.
