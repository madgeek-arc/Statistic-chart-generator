# Angular Integration

Two query modes available — both POST to the same `/chart` endpoint:

- **DSL** — structured JSON (fast, predictable, no LLM)
- **NL** — natural language via Claude agent (conversational)

---

## DSL Quick Start

```typescript
@Injectable({ providedIn: 'root' })
export class ChartService {
  private base = 'http://localhost:8090/stats-api';
  constructor(private http: HttpClient) {}

  getChart(request: ChartRequest): Observable<unknown> {
    return this.http.post(`${this.base}/chart`, request);
  }

  getEntities(profile: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/schema/${profile}/entities`);
  }

  getEntityFields(profile: string, entity: string): Observable<any> {
    return this.http.get(`${this.base}/schema/${profile}/entities/${entity}`);
  }

  getFieldValues(profile: string, field: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/schema/${profile}/fields/${field}`);
  }
}
```

### Example request — publications per year

```typescript
const request: ChartRequest = {
  library: 'HighCharts',
  chartsInfo: [{
    name: 'Publications by year',
    type: 'bar',
    color: '#2f7ed8',
    query: {
      entity:  'publication',
      profile: 'openaire',
      select: [
        { field: 'publication', aggregate: 'count', order: 1 },
        { field: 'publication.year', order: 2 }
      ],
      filters: [{
        groupFilters: [
          { field: 'publication.year', type: 'between', values: ['2010', '2020'] },
          { field: 'publication.funder', type: '=', values: ['H2020'] }
        ],
        op: 'AND'
      }],
      limit:    20,
      useCache: true
    }
  }]
};
```

HighCharts response:
```json
{
  "series": [{ "data": [150, 200, 240, 310] }],
  "xAxis_categories": ["2010", "2011", "2012", "2013"],
  "dataSeriesNames": ["Publications by year"],
  "dataSeriesTypes": ["bar"]
}
```

### Multi-series with shared x-axis

```typescript
const request: ChartRequest = {
  library: 'GoogleCharts',
  orderBy: 'xaxis',
  chartsInfo: [
    { name: 'H2020', query: { entity: 'publication', profile: 'openaire', select: [...], filters: [{ groupFilters: [{ field: '...', type: '=', values: ['H2020'] }], op: 'AND' }] } },
    { name: 'FP7',   query: { entity: 'publication', profile: 'openaire', select: [...], filters: [{ groupFilters: [{ field: '...', type: '=', values: ['FP7']   }], op: 'AND' }] } }
  ]
};
```

GoogleCharts response:
```json
{
  "dataTable": [["2010", 150, 80], ["2011", 200, 90]],
  "columns": ["Year", "H2020", "FP7"],
  "columnsType": ["string", "number", "number"]
}
```

---

## TypeScript Types

```typescript
interface ChartRequest {
  library:    'HighCharts' | 'GoogleCharts' | 'ECharts';
  chartsInfo: ChartInfo[];
  orderBy?:   null | 'xaxis' | 'stacked' | 'pinned' | 'yaxis';
  // NL options (optional)
  nlOptions?:  string;
  optionsSig?: string;
}

interface ChartInfo {
  name?:  string;
  type?:  'bar' | 'column' | 'line' | 'area' | 'pie';
  color?: string;          // hex, e.g. '#2f7ed8'
  query:  DslQuery | NlQuery | NamedQuery;
}

interface DslQuery {
  entity:    string;
  profile?:  string;
  select:    Select[];
  filters?:  FilterGroup[];
  limit?:    number;        // 0 = unlimited
  useCache?: boolean;       // default true
}

interface Select {
  field:      string;       // 'entity' or 'entity.field' or 'entity.relation.field'
  aggregate?: 'count' | 'sum' | 'avg' | 'min' | 'max';
  order?:     number;       // 1 = y-axis, 2+ = x-axis dimensions
}

interface FilterGroup {
  groupFilters: Filter[];
  op: 'AND' | 'OR';
}

interface Filter {
  field:  string;
  type:   '=' | '!=' | '>' | '>=' | '<' | '<=' | 'between' | 'in' | 'not_in'
        | 'contains' | 'startsWith' | 'is_null' | 'is_not_null';
  values: string[];         // omit for is_null / is_not_null; exactly 2 for between
}

// NL query — from completed /nl/chat session
interface NlQuery {
  nl:      string;
  sig:     string;
  profile: string;
  filters?: FilterGroup[];
}

// Pre-configured server-side SQL
interface NamedQuery {
  name:        string;
  parameters?: any[];
}
```

---

## Schema Discovery

```typescript
// All visible profiles
GET /schema/profiles
→ [{ name: 'openaire', description: '...', primary: true }]

// Entities in a profile
GET /schema/openaire/entities
→ ['publication', 'dataset', 'project', 'datasource', 'organization']

// Fields on an entity
GET /schema/openaire/entities/publication
→ { fields: [{ name: 'year', datatype: 'int' }, { name: 'access mode', datatype: 'text' }] }

// Distinct values for filter dropdowns
GET /schema/openaire/fields/publication.year
→ ['2018', '2019', '2020', '2021', '2022', '2023']
```

---

## Other Endpoints

```typescript
// Tabular data — same request format as /chart
POST /table

// Raw unformatted data
GET /raw?json={"series":[{"query":{...}}],"verbose":false}
// → { "data": [[[2019,100],[2020,200]]] }

// Shorten a chart URL
POST /chart/shorten  { "url": "https://..." }
// → { "shortUrl": "https://tinyurl.com/..." }
```

---

## CORS

All endpoints allow `*` origins. No proxy configuration needed.

---

## NL (Natural Language) Flow

```
POST /nl/chat { profile, message }
  ↓  (may repeat several turns until done: true)
  queryJson = { nl, sig, profile }
  ↓
POST /nl/options/chat { library, message }   ← optional chart styling
  ↓  (may repeat until done: true)
  optionsElement = { nlOptions, optionsSig }
  ↓
POST /chart {
  library: 'HighCharts',
  chartsInfo: [{ type: 'bar', query: queryJson }],
  ...optionsElement   ← spread at top level (omit if skipping options)
}
```

```typescript
// Minimal NL service
chat(profile: string, message: string, sessionId?: string) {
  return this.http.post(`${this.base}/nl/chat`, { profile, message, sessionId });
}
// Echo sessionId in every follow-up until response.done === true
// Then use response.queryJson directly as query in /chart
```

For the full NL integration with TypeScript interfaces, component code, and conversation examples see `ChartDataFormatter/docs/APIReference.md` — NL Query Endpoints section.
