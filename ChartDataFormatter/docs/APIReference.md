# API Reference

Complete reference for all JSON request and response parameters exposed by the Statistic Chart Generator API.

---

## Endpoints

| Method | Path | Request format | Response format |
|--------|------|---------------|-----------------|
| `POST` | `/chart` | `RequestInfo` (JSON body) | `JsonResponse` |
| `GET` | `/chart/json?json=` | `RequestInfo` (URL-encoded JSON param) | `JsonResponse` |
| `GET` | `/chart` | `chartDescription` (URL-encoded JSON param, see [HTML page format](#html-page-url-format)) | HTML page |
| `POST` | `/table` | `RequestInfo` (JSON body) | `JsonResponse` |
| `GET` | `/raw?json=` | `RawDataRequestInfo` (URL-encoded JSON param) | `JsonResponse` |
| `POST` | `/chart/shorten` | `ShortenUrlInfo` (JSON body) | `{ "shortUrl": "..." }` |

> **Note:** The `/chart` GET endpoint returns an HTML chart viewer page — it does **not** return data JSON. The data comes from the separate `/chart/json` GET or `/chart` POST endpoint using a different JSON structure (see below).

---

## Request Formats

### `RequestInfo` — used by `/chart` (POST), `/chart/json` (GET), `/table` (POST)

```json
{
  "library":    "HighCharts",
  "orderBy":    "pinned",
  "drilldown":  false,
  "chartsInfo": [ <ChartInfo>, ... ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `library` | `string` | **yes** | Chart library. One of: `"HighCharts"`, `"GoogleCharts"`, `"ECharts"`. |
| `chartsInfo` | `ChartInfo[]` | **yes** | One entry per data series (see [ChartInfo](#chartinfo)). |
| `orderBy` | `string` | no | Controls x-axis ordering for multi-query merging. See [orderBy values](#orderby-values). Defaults to `null`. |
| `drilldown` | `boolean` | no | Enables drilldown series in HighCharts / ECharts responses. Default `false`. |

---

#### `ChartInfo`

One element in `chartsInfo`. Maps a visual series to a data query.

```json
{
  "name":  "Repository OA",
  "type":  "column",
  "color": "#26580f",
  "query": { <Query> }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | no | Series label shown in the chart legend. |
| `type` | `string` | no | Per-series chart type override (e.g. `"column"`, `"line"`, `"area"`, `"bar"`, `"pie"`). Falls back to the library default if omitted. |
| `color` | `string` | no | Hex color code for the series (e.g. `"#26580f"`). |
| `query` | `Query` | **yes** | Data query (see [Query](#query)). |

---

#### `Query`

Describes what data to fetch from the database.

```json
{
  "entity":     "publication",
  "profile":    "openaire_stats",
  "select":     [ <Select>, ... ],
  "filters":    [ <FilterGroup>, ... ],
  "parameters": [],
  "limit":      30,
  "orderBy":    null,
  "useCache":   true
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `entity` | `string` | **yes** | Logical entity name as defined in the profile (e.g. `"publication"`, `"project"`, `"result"`, `"cross_country"`, `"historical_snapshots_irish"`). |
| `profile` | `string` | no | Profile key that determines the datasource and field mappings (e.g. `"openaire_stats"`, `"ie_monitor"`). If omitted, the default profile is used. |
| `select` | `Select[]` | no | Fields to return. Typically one aggregate field + one or more group-by fields (see [Select](#select)). |
| `filters` | `FilterGroup[]` | no | Filter groups applied as a WHERE clause (see [FilterGroup](#filtergroup)). Multiple groups are combined with AND. |
| `parameters` | `any[]` | no | Positional parameters for named queries. Usually `[]`. |
| `limit` | `integer` | no | Maximum number of rows to return. `0` means no limit. The merged query applies the minimum positive limit across all queries in a request. |
| `orderBy` | `string` | no | Per-query ORDER BY passed to `SqlQueryBuilder`. Rarely used directly; the top-level `RequestInfo.orderBy` controls the outer merge order. |
| `useCache` | `boolean` | no | Whether to use the result cache. Default `true`. |

---

#### `Select`

One item in `Query.select`. Defines a column in the result set.

```json
{ "field": "publication.year", "aggregate": null, "order": 2 }
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `field` | `string` | **yes** | Dot-separated field path: `"entity.fieldName"` or `"entity.relation.fieldName"` (e.g. `"publication.year"`, `"project.publication.year"`, `"historical_snapshots_irish.total"`). Use just the entity name (e.g. `"publication"`) when `aggregate` is set — the mapper resolves it to the primary key. |
| `aggregate` | `string` | no | SQL aggregate function. One of: `"count"`, `"sum"`, `"avg"`, `"min"`, `"max"`. Omit or set `null` for a plain GROUP BY dimension. |
| `order` | `integer` | no | Relative position of this field in the output. Used when multiple group-by fields exist (e.g. `1` = y column, `2` = first x column). Typically the aggregate field gets `order=1`, group-by fields get `order=2`, `3`, … |

**Convention:** The first `Select` entry is the y-axis (aggregate); subsequent entries are x-axis dimensions (GROUP BY fields). For multi-query merging, the first group-by field becomes `x1`.

---

#### `FilterGroup`

A logical group of filters. Multiple `FilterGroup` entries in `Query.filters` are always combined with AND between groups.

```json
{
  "groupFilters": [ <Filter>, ... ],
  "op": "AND"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `groupFilters` | `Filter[]` | **yes** | One or more filters in this group. |
| `op` | `string` | **yes** | How to combine the filters within this group. `"AND"` or `"OR"`. |

**SQL mapping:**
- `op="AND"`: filters within the group become `(col1=? AND col2=? AND …)` — generated as an `EXISTS` subquery against a joined table, or as plain `AND` conditions on the root table.
- `op="OR"`: filters within the group become `(col1=? OR col2=? OR …)` on the root table (Impala-compatible plain OR; no correlated subquery).

---

#### `Filter`

A single predicate applied to one field.

```json
{
  "field":    "publication.year",
  "type":     "between",
  "values":   ["2010", "2020"],
  "datatype": "int"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `field` | `string` | **yes** | Dot-separated field path (same format as `Select.field`). |
| `type` | `string` | **yes** | Filter operator. See [filter operators](#filter-operators). |
| `values` | `string[]` | **yes** | Values to test against. All values are strings in JSON; `datatype` controls SQL casting. For `between`, provide exactly two values: `[min, max]`. For `=` / `!=` with multiple values, generates `IN` / `NOT IN`. |
| `datatype` | `string` | no | SQL cast hint. Common values: `"int"`, `"text"`, `"date"`. If omitted the mapper infers from the field type in the profile. |

##### Filter operators

| `type` value | SQL generated | Notes |
|---|---|---|
| `"="` | `col = ?` or `col IN (?,?,…)` | Single value → `=`; multiple values → `IN` |
| `"!="` | `col != ?` or `col NOT IN (?,?,…)` | Single value → `!=`; multiple values → `NOT IN` |
| `">"` | `col > ?` | |
| `">="` | `col >= ?` | |
| `"<"` | `col < ?` | |
| `"<="` | `col <= ?` | |
| `"between"` | `col BETWEEN ? AND ?` | Requires exactly 2 values |
| `"contains"` | `col LIKE ?` | Value is wrapped with `%…%` |
| `"startsWith"` | `col LIKE ?` | Value is wrapped with `…%` |

---

### `orderBy` values

The top-level `RequestInfo.orderBy` controls how multi-query results are merged and ordered. It has no effect on single-query requests.

| Value | `stackedOrder` | x-axis driver | Final ORDER BY | Use case |
|-------|---------------|---------------|----------------|----------|
| `null` (omitted) | true | `keys` CTE — union of all queries | `x1` alphabetically | Stacked/categorical charts where each query covers a different category; all x-values must appear |
| `"xaxis"` | false | `q1` LEFT JOIN | `x1` alphabetically | Line/time-series charts aligned by a shared x-axis dimension |
| `"stacked"` | true | `keys` CTE | `COALESCE(y1,0)+…+COALESCE(yn,0) DESC` | Same as null but sorted by combined value descending |
| `"pinned"` | true | `keys` CTE | `CASE WHEN y1 IS NOT NULL THEN 0 ELSE 1 END, COALESCE sum DESC` | q1's x-values (e.g. a reference country) always appear first; rest sorted by value |
| `"yaxis"` | false | `q1` LEFT JOIN | `1 DESC` (first y column) | Ranked bar charts where q1 defines the top-N; q2…qN provide additional series for the same x-values |

**`pinned` detail:** q1 should contain the "always-include" reference set (e.g. `country='Ireland'`). The `keys` CTE guarantees those entries appear even if q2 has no matching rows. The ORDER BY pins rows where `y1 IS NOT NULL` (q1's entries) to the front (group `0`), followed by the remaining rows sorted by combined sum descending (group `1`).

---

### `RawDataRequestInfo` — used by `/raw` (GET)

```json
{
  "series":  [ { "query": { <Query> } }, ... ],
  "orderBy": null,
  "verbose": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `series` | `RawDataSeriesInfo[]` | **yes** | One entry per series, each wrapping a `Query`. |
| `orderBy` | `string` | no | Same values as `RequestInfo.orderBy`. |
| `verbose` | `boolean` | no | `false` → compact `data` response (3D array). `true` → verbose response including original query objects and labelled rows. |

---

### `ShortenUrlInfo` — used by `/chart/shorten` (POST)

```json
{ "url": "https://stats.example.org/stats-api/chart?json=..." }
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `url` | `string` | **yes** | Full chart URL to shorten via TinyURL. |

---

## HTML Page URL Format

The `GET /chart?json=` endpoint accepts a **different** JSON structure intended for the browser chart viewer. The JavaScript in the page then calls `/chart` (POST) with the API format internally.

```json
{
  "library": "HighCharts",
  "orderBy": "pinned",
  "chartDescription": {
    "chart":  { "type": "line" },
    "title":  { "text": "Repository OA vs Publisher OA" },
    "xAxis":  { "title": { "text": "Year" } },
    "yAxis":  { "title": { "text": "Count" } },
    "series": [ { "stacking": "normal" }, { "stacking": "normal" } ],
    "colors": ["#26580fda", "#4031c0ff"],
    "legend": { "enabled": true },
    "queries": [
      {
        "name":  "Repository OA",
        "type":  "column",
        "color": "#26580f",
        "query": { <Query> }
      }
    ]
  }
}
```

`chartDescription` is a HighCharts / Google Charts / ECharts configuration object with an additional `queries` array that mirrors the `chartsInfo` format. All HighCharts plot options, axes, title, legend, tooltip, and exporting settings are passed through to the frontend as-is.

> Do **not** use this format to call the data API directly. Use `RequestInfo` (with `chartsInfo`) for programmatic access.

---

## Response Formats

### HighCharts — `HighChartsJsonResponse`

Returned when `library = "HighCharts"` or `"ECharts"`.

```json
{
  "series": [
    { "data": [100, 200, null, 300] },
    { "data": [50,  null, 150, 250] }
  ],
  "xAxis_categories": ["2021", "2022", "2023", "2024"],
  "dataSeriesNames":  ["H2020", "FP7"],
  "dataSeriesTypes":  ["column", "line"],
  "drilldown": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `series` | `AbsData[]` | One element per `ChartInfo` entry. Data values aligned to `xAxis_categories` (missing values are `null`). See [AbsData variants](#absdata-variants). |
| `xAxis_categories` | `string[]` | Ordered x-axis labels. |
| `dataSeriesNames` | `string[]` | Series names (one per `series` entry, matches `ChartInfo.name`). |
| `dataSeriesTypes` | `string[]` | Per-series chart type (one per `series` entry, matches `ChartInfo.type`). |
| `drilldown` | `AbsData[]` | Drilldown series; populated only when `drilldown: true` in the request. |

#### `AbsData` variants

The `series[i].data` field is polymorphic:

| Variant | When used | Shape |
|---------|-----------|-------|
| `ArrayOfValues` | Single numeric column | `"data": [100, 200, 300]` |
| `ArrayOfArrays` | Multiple numeric columns | `"data": [[x, y], [x, y]]` |
| `ArrayOfDataObjects` | Named / styled points | `"data": [{"name": "IE", "y": 519937, "color": "#ff0"}]` |
| `GraphData` | Network graphs | `"keys": ["from","to","weight"], "data": [["A","B",5]]` |

---

### Google Charts — `GoogleChartsJsonResponse`

Returned when `library = "GoogleCharts"`.

```json
{
  "dataTable":   [[2021, 100, 50], [2022, 200, null], [2023, null, 150]],
  "columns":     ["Year", "H2020", "FP7"],
  "columnsType": ["string", "number", "number"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `dataTable` | `any[][]` | Rows × columns. First row is typically the x-axis value; remaining columns are y-values per series. |
| `columns` | `string[]` | Column header names. |
| `columnsType` | `string[]` | Google Charts data type per column (`"string"`, `"number"`, `"date"`). |

---

### Raw data — `RawDataJsonResponse` (compact)

Returned by `/raw` when `verbose: false`.

```json
{
  "data": [
    [[2021, 100], [2022, 200]],
    [[2021, 50],  [2023, 150]]
  ]
}
```

`data[i]` is the result for series `i`; each inner array is one row of values.

---

### Raw data — `VerboseRawDataResponse` (verbose)

Returned by `/raw` when `verbose: true`.

```json
{
  "datasets": [
    {
      "series": {
        "query":  { <Query> },
        "result": [
          { "row": [2021, 100] },
          { "row": [2022, 200] }
        ]
      }
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `datasets` | array | One entry per series. |
| `datasets[i].series.query` | `Query` | The original query object that produced this series. |
| `datasets[i].series.result` | array | Rows returned. Each `row` is an array of column values. |

---

## Full Examples

### Single-query HighCharts bar chart

```json
POST /chart
{
  "library": "HighCharts",
  "chartsInfo": [
    {
      "name":  "Publications by year (H2020)",
      "type":  "bar",
      "color": "#2f7ed8",
      "query": {
        "entity":  "project",
        "profile": "openaire_stats",
        "limit":   20,
        "select": [
          { "field": "project",                  "aggregate": "count", "order": 1 },
          { "field": "project.publication.year", "aggregate": null,    "order": 2 }
        ],
        "filters": [
          {
            "groupFilters": [
              { "field": "project.publication.year",    "type": "between", "values": ["2010","2020"] },
              { "field": "project.funding level 0",     "type": "=",       "values": ["H2020"] }
            ],
            "op": "AND"
          }
        ],
        "parameters": [],
        "useCache": true
      }
    }
  ]
}
```

---

### Multi-query stacked chart (null orderBy — keys CTE)

All access-mode categories appear on the x-axis even though each query returns only one category.

```json
POST /chart
{
  "library": "HighCharts",
  "chartsInfo": [
    {
      "name": "Open Access",
      "type": "column",
      "query": {
        "entity": "result", "profile": "openaire_stats",
        "select": [
          { "field": "result",             "aggregate": "count" },
          { "field": "result.access_mode", "aggregate": null    }
        ],
        "filters": [
          { "groupFilters": [
              { "field": "result.access_mode", "type": "=", "values": ["Open Access"] }
            ], "op": "AND" }
        ]
      }
    },
    {
      "name": "Embargo",
      "type": "column",
      "query": {
        "entity": "result", "profile": "openaire_stats",
        "select": [
          { "field": "result",             "aggregate": "count" },
          { "field": "result.access_mode", "aggregate": null    }
        ],
        "filters": [
          { "groupFilters": [
              { "field": "result.access_mode", "type": "=", "values": ["Embargo"] }
            ], "op": "AND" }
        ]
      }
    }
  ]
}
```

---

### Pinned reference + peer comparison (`orderBy="pinned"`)

Ireland always appears first; other countries sorted by value descending.

```json
POST /chart
{
  "library": "HighCharts",
  "orderBy": "pinned",
  "chartsInfo": [
    {
      "name":  "Irish peer reviewed publications",
      "type":  "column",
      "color": "#ff6d00",
      "query": {
        "entity": "cross_country", "profile": "openaire_stats", "limit": 50,
        "select": [
          { "field": "cross_country.total",   "aggregate": "sum"  },
          { "field": "cross_country.country", "aggregate": null   }
        ],
        "filters": [
          { "groupFilters": [
              { "field": "cross_country.refereed", "type": "=",  "values": ["peerReviewed"] },
              { "field": "cross_country.country",  "type": "=",  "values": ["Ireland"]      },
              { "field": "cross_country.type",     "type": "=",  "values": ["publication"]  }
            ], "op": "AND" }
        ]
      }
    },
    {
      "name":  "Peer reviewed publications (other countries)",
      "type":  "column",
      "color": "#2f7ed8",
      "query": {
        "entity": "cross_country", "profile": "openaire_stats", "limit": 50,
        "select": [
          { "field": "cross_country.total",   "aggregate": "sum"  },
          { "field": "cross_country.country", "aggregate": null   }
        ],
        "filters": [
          { "groupFilters": [
              { "field": "cross_country.refereed", "type": "=",  "values": ["peerReviewed"] },
              { "field": "cross_country.country",  "type": "!=", "values": ["Ireland"]      },
              { "field": "cross_country.type",     "type": "=",  "values": ["publication"]  }
            ], "op": "AND" }
        ]
      }
    }
  ]
}
```

---

### Raw data (verbose)

```json
GET /raw?json={"series":[{"query":{"entity":"publication","profile":"openaire_stats","select":[{"field":"publication","aggregate":"count"},{"field":"publication.year","aggregate":null}],"filters":[]}}],"verbose":true}
```

Response:

```json
{
  "datasets": [
    {
      "series": {
        "query": { "entity": "publication", ... },
        "result": [
          { "row": [2020, 15432] },
          { "row": [2021, 18901] }
        ]
      }
    }
  ]
}
```
