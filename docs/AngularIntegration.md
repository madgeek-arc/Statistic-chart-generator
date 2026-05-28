# Angular Integration Guide — Natural Language Query & Options Agent

This guide shows a beginner-friendly Angular integration for the NL chat APIs.
No prior experience with this backend is assumed; each step is explained.

---

## What the flow looks like

```
User types a question
        │
        ▼
Angular sends POST /nl/chat
        │
        ▼
Backend asks Claude for clarifications (if needed)
        │  ← may repeat several turns
        ▼
Claude is satisfied → backend returns  done: true  +  queryJson {nl, sig, profile}
        │
        ▼
(Optional) User describes chart appearance
        │
        ▼
Angular sends POST /nl/options/chat
        │  ← may repeat several turns
        ▼
Claude is satisfied → backend returns  done: true  +  optionsElement {nlOptions, optionsSig}
        │
        ▼
Angular sends POST /stats-api/chart  (with query.nl + query.sig + nlOptions + optionsSig)
        │
        ▼
Angular renders the chart (HighCharts / GoogleCharts / eCharts)
```

---

## End-to-end curl walkthrough

This section shows every API call in sequence using plain `curl`.
Copy-paste these to verify connectivity before writing any Angular code.
Replace `localhost:8090` with the actual backend host.

---

### Step 1 — Start a data query session

Send the first message with no `sessionId`:

```sh
curl -s -X POST http://localhost:8090/stats-api/nl/chat \
  -H 'Content-Type: application/json' \
  -d '{"profile":"openaire","message":"Show me publications per year"}'
```

The agent may ask for clarification — `done` is `false`:

```json
{
  "sessionId": "abc123",
  "reply": "Do you want to include only open access publications, or all types?",
  "done": false
}
```

---

### Step 2 — Continue the conversation

Echo `sessionId` in every follow-up:

```sh
curl -s -X POST http://localhost:8090/stats-api/nl/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"abc123","profile":"openaire","message":"All types"}'
```

When the agent is satisfied it returns `done: true` with the signed query:

```json
{
  "sessionId": "abc123",
  "reply": "Got it. I've set up a query for publications per year across all types.",
  "done": true,
  "canonicalNl": "publications per year",
  "sig": "v1:a3f9e2c1...",
  "sql": "SELECT year, COUNT(*) FROM result GROUP BY year ORDER BY year",
  "description": "Number of publications grouped by publication year.",
  "queryJson": {
    "nl": "publications per year",
    "sig": "v1:a3f9e2c1...",
    "profile": "openaire"
  }
}
```

**Save `queryJson`** — drop it directly into the `query` field of a `chartsInfo` entry.
`canonicalNl`, `sig`, and `description` are also returned individually for display purposes.

---

### Step 3 — (Optional) Design chart appearance

Start a separate options session with the target charting library:

```sh
curl -s -X POST http://localhost:8090/stats-api/nl/options/chat \
  -H 'Content-Type: application/json' \
  -d '{"library":"HighCharts","message":"Blue bars with a red title"}'
```

Agent asks a follow-up (`done: false`):

```json
{
  "sessionId": "def456",
  "reply": "Blue bars and red title noted. Move the legend anywhere, or keep it at the bottom?",
  "done": false
}
```

Continue the options conversation:

```sh
curl -s -X POST http://localhost:8090/stats-api/nl/options/chat \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"def456","library":"HighCharts","message":"Keep the legend at the bottom"}'
```

Final response (`done: true`):

```json
{
  "sessionId": "def456",
  "reply": "Signed. Your chart options are ready.",
  "done": true,
  "canonicalDescription": "blue bars, red title",
  "sig": "v1:c7d1b4e8...",
  "optionsJson": "{\"colors\":[\"#0033cc\"],\"title\":{\"style\":{\"color\":\"red\"}}}",
  "optionsElement": {
    "nlOptions": "blue bars, red title",
    "optionsSig": "v1:c7d1b4e8..."
  }
}
```

**Save `optionsElement`** — spread it directly into the chart request body.

---

### Step 4 — Fetch chart data

`POST /chart` using `queryJson` from step 2 and `optionsElement` from step 3 (if run):

```sh
curl -s -X POST http://localhost:8090/stats-api/chart \
  -H 'Content-Type: application/json' \
  -d '{
    "library": "HighCharts",
    "chartsInfo": [{
      "type": "bar",
      "name": "Publications per year",
      "query": <queryJson from step 2>
    }],
    <optionsElement from step 3, spread at top level>
  }'
```

Concretely:

```sh
curl -s -X POST http://localhost:8090/stats-api/chart \
  -H 'Content-Type: application/json' \
  -d '{
    "library": "HighCharts",
    "chartsInfo": [{
      "type": "bar",
      "name": "Publications per year",
      "query": {
        "nl":      "publications per year",
        "sig":     "v1:a3f9e2c1...",
        "profile": "openaire"
      }
    }],
    "nlOptions":   "blue bars, red title",
    "optionsSig":  "v1:c7d1b4e8..."
  }'
```

Response — chart series data plus merged chart options:

```json
{
  "series": [
    { "name": "Publications", "data": [12050, 15340, 19200, 22870, 27630] }
  ],
  "xAxis": { "categories": ["2019", "2020", "2021", "2022", "2023"] },
  "chartOptions": {
    "colors": ["#0033cc"],
    "title": { "style": { "color": "red" } }
  }
}
```

To skip the options step, simply omit `nlOptions` and `optionsSig`:

```sh
curl -s -X POST http://localhost:8090/stats-api/chart \
  -H 'Content-Type: application/json' \
  -d '{
    "library": "HighCharts",
    "chartsInfo": [{
      "type": "bar",
      "query": {
        "nl":      "publications per year",
        "sig":     "v1:a3f9e2c1...",
        "profile": "openaire"
      }
    }]
  }'
```

---

### Step 5 — Retrieve query metadata (URL-loading scenario)

When a user loads a saved chart URL you already have `canonicalNl` + `sig` + `profile`.
Call `GET /nl/info` to display the SQL and description without touching the LLM:

```sh
curl -s "http://localhost:8090/stats-api/nl/info\
?profile=openaire\
&nl=publications%20per%20year\
&sig=v1%3Aa3f9e2c1..."
```

Response:

```json
{
  "sql": "SELECT year, COUNT(*) FROM result GROUP BY year ORDER BY year",
  "description": "Number of publications grouped by publication year."
}
```

Possible error codes: `403` = signature invalid, `404` = not in cache (execute the chart first, then retry).

If the query carries dashboard-injected filters, URL-encode them too:

```sh
curl -s "http://localhost:8090/stats-api/nl/info\
?profile=openaire\
&nl=publications%20per%20year\
&sig=v1%3Axxx\
&filters=%5B%7B%22groupFilters%22%3A%5B%7B%22field%22%3A%22type%22%2C%22type%22%3A%22%3D%22%2C%22values%22%3A%5B%22Article%22%5D%7D%5D%2C%22op%22%3A%22AND%22%7D%5D"
```

---

### Step 6 — (Trusted backends only) Pre-sign a query with filters

Dashboard backends that need to inject filter conditions call `POST /nl/sign` before handing the URL to the browser. This endpoint requires the `X-Sign-Key` header and is never called from the browser:

```sh
curl -s -X POST http://localhost:8090/stats-api/nl/sign \
  -H 'Content-Type: application/json' \
  -H 'X-Sign-Key: your-sign-key-here' \
  -d '{
    "profile":     "openaire",
    "canonicalNl": "publications per year",
    "filters": [
      {
        "groupFilters": [
          { "field": "type", "type": "=", "values": ["Article"] }
        ],
        "op": "AND"
      }
    ]
  }'
```

Response:

```json
{
  "sig":              "v1:9b2f3e7d...",
  "canonicalFilters": "type=Article"
}
```

The backend then passes `sig` and the serialised `filters` array to the browser inside the chart URL. The browser includes both in the `query` object when calling `/chart`:

```jsonc
{
  "nl":      "publications per year",
  "sig":     "v1:9b2f3e7d...",
  "profile": "openaire",
  "filters": [
    { "groupFilters": [{ "field": "type", "type": "=", "values": ["Article"] }], "op": "AND" }
  ]
}
```

> **Security note:** never expose `sign-key` to the browser. Only trusted server-side code calls `POST /nl/sign`.

---

## 1. Create a service

```typescript
// src/app/nl-chat/nl-chat.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// --- NL query (data) ---

export interface ChatRequest {
  sessionId?: string;   // omit on first message; the server creates one
  profile: string;      // e.g. "openaire_stats"
  message: string;
}

export interface ChatResponse {
  sessionId: string;    // keep this and send it back on every subsequent turn
  reply: string;        // the assistant's text to show the user
  done: boolean;        // true when the query is fully resolved
  canonicalNl?: string; // populated when done — human-readable summary of the query (display only)
  sig?: string;         // populated when done — HMAC signature (also inside queryJson)
  sql?: string;         // populated when done — the generated SQL (informational)
  description?: string; // populated when done — plain-English sentence describing the query results
  queryJson?: NlQuery;  // populated when done — drop directly into chartsInfo[i].query
}

// --- NL options (appearance) ---

export interface OptionsRequest {
  sessionId?: string;   // omit on first message; the server creates one
  library: string;      // "HighCharts", "eCharts", or "GoogleCharts"
  message: string;
}

export interface OptionsResponse {
  sessionId: string;
  reply: string;
  done: boolean;
  canonicalDescription?: string;                        // populated when done — for display
  sig?: string;                                         // populated when done — also inside optionsElement
  optionsJson?: string;                                 // populated when done — raw chart options JSON (informational)
  optionsElement?: { nlOptions: string; optionsSig: string }; // populated when done — spread directly into chart request body
}

// --- Chart request ---

export interface ChartRequest {
  library: string;      // "HighCharts", "GoogleCharts", or "eCharts"
  chartsInfo: ChartInfo[];
  orderBy?: string;
  nlOptions?: string;   // canonicalDescription from OptionsResponse (optional)
  optionsSig?: string;  // sig from OptionsResponse (optional)
}

export interface ChartInfo {
  type: string;         // "bar", "line", "pie", etc.
  name?: string;
  query: NlQuery | DslQuery;
}

// NL query — produced from a completed chat session
export interface NlQuery {
  nl: string;           // canonicalNl from ChatResponse
  sig: string;          // sig from ChatResponse
  profile: string;      // same profile you passed to /nl/chat
  filters?: FilterGroup[]; // optional dashboard-injected filters (sig must cover these)
}

export interface FilterGroup {
  groupFilters: Filter[];
  op: 'AND' | 'OR';
}

export interface Filter {
  field: string;
  type: string;
  values: string[];
}

// DSL query — existing JSON DSL (unchanged)
export interface DslQuery {
  entity: string;
  select: object[];
  // ...other DSL fields
}

@Injectable({ providedIn: 'root' })
export class NlChatService {
  // Change this if your backend runs on a different host/port
  private readonly apiBase = 'http://localhost:8090/stats-api';

  constructor(private http: HttpClient) {}

  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(
      `${this.apiBase}/nl/chat`,
      request,
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  optionsChat(request: OptionsRequest): Observable<OptionsResponse> {
    return this.http.post<OptionsResponse>(
      `${this.apiBase}/nl/options/chat`,
      request,
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  fetchChart(chartRequest: ChartRequest): Observable<unknown> {
    return this.http.post<unknown>(
      `${this.apiBase}/chart`,
      chartRequest,
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  /**
   * Returns the SQL and description for a previously signed NL query without executing it.
   * Use this when loading a chart URL to show metadata (e.g. a "Query info" panel).
   * Returns null if the query is not in cache (chart must be executed first).
   */
  nlInfo(profile: string, nl: string, sig: string,
         filters?: FilterGroup[]): Observable<{ sql: string; description: string }> {
    let params = `profile=${encodeURIComponent(profile)}&nl=${encodeURIComponent(nl)}&sig=${encodeURIComponent(sig)}`;
    if (filters?.length) {
      params += `&filters=${encodeURIComponent(JSON.stringify(filters))}`;
    }
    return this.http.get<{ sql: string; description: string }>(
      `${this.apiBase}/nl/info?${params}`
    );
  }
}
```

> **Why `sessionId`?** The backend keeps the conversation history server-side.
> You must echo the `sessionId` you received back in every follow-up message so
> the server can find the right history.

---

## 2. Create a chat component

```typescript
// src/app/nl-chat/nl-chat.component.ts
import { Component } from '@angular/core';
import { NlChatService, ChatResponse, OptionsResponse, NlQuery } from './nl-chat.service';

interface Message {
  role: 'user' | 'assistant';
  text: string;
}

@Component({
  selector: 'app-nl-chat',
  templateUrl: './nl-chat.component.html',
})
export class NlChatComponent {
  profile = 'openaire_stats';  // swap for the profile the user selected
  library = 'HighCharts';

  // NL query conversation state
  queryMessages: Message[] = [];
  querySessionId?: string;
  queryJson?: NlQuery;          // ready-to-embed query object (queryJson from ChatResponse)
  queryDescription?: string;    // plain-English description of the query results (display only)

  // Options conversation state
  optionsMessages: Message[] = [];
  optionsSessionId?: string;
  optionsElement?: { nlOptions: string; optionsSig: string }; // ready-to-spread into chart request

  inputText = '';
  loading = false;
  phase: 'query' | 'options' | 'done' = 'query';
  chartData: unknown = null;

  constructor(private nlChat: NlChatService) {}

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.loading) return;
    this.inputText = '';
    this.loading = true;

    if (this.phase === 'query') {
      this.sendQueryMessage(text);
    } else if (this.phase === 'options') {
      this.sendOptionsMessage(text);
    }
  }

  private sendQueryMessage(text: string): void {
    this.queryMessages.push({ role: 'user', text });

    this.nlChat.chat({
      sessionId: this.querySessionId,
      profile: this.profile,
      message: text,
    }).subscribe({
      next: (res: ChatResponse) => {
        this.querySessionId = res.sessionId;
        this.queryMessages.push({ role: 'assistant', text: res.reply });
        this.loading = false;

        if (res.done && res.queryJson) {
          this.queryJson = res.queryJson;
          this.queryDescription = res.description;  // show in read-only "Query info" panel
          this.phase = 'options';  // move to appearance conversation
        }
      },
      error: (err) => {
        console.error('NL chat error', err);
        this.queryMessages.push({ role: 'assistant', text: 'Sorry, something went wrong.' });
        this.loading = false;
      },
    });
  }

  private sendOptionsMessage(text: string): void {
    this.optionsMessages.push({ role: 'user', text });

    this.nlChat.optionsChat({
      sessionId: this.optionsSessionId,
      library: this.library,
      message: text,
    }).subscribe({
      next: (res: OptionsResponse) => {
        this.optionsSessionId = res.sessionId;
        this.optionsMessages.push({ role: 'assistant', text: res.reply });
        this.loading = false;

        if (res.done && res.optionsElement) {
          this.optionsElement = res.optionsElement;
          this.phase = 'done';
          this.loadChart();
        }
      },
      error: (err) => {
        console.error('Options chat error', err);
        this.optionsMessages.push({ role: 'assistant', text: 'Sorry, something went wrong.' });
        this.loading = false;
      },
    });
  }

  skipOptions(): void {
    // User skips the appearance conversation — fetch chart without options
    this.phase = 'done';
    this.loadChart();
  }

  private loadChart(): void {
    this.nlChat.fetchChart({
      library: this.library,
      chartsInfo: [{
        type: 'bar',
        name: this.queryJson!.nl,
        query: this.queryJson!,             // drop queryJson directly here
      }],
      // Spread optionsElement at top level — undefined is ignored by the spread
      ...this.optionsElement,
    }).subscribe({
      next: (data) => {
        this.chartData = data;
        console.log('Chart data ready:', data);
      },
      error: (err) => console.error('Chart fetch error', err),
    });
  }
}
```

---

## 3. Create the template

```html
<!-- src/app/nl-chat/nl-chat.component.html -->
<div class="nl-chat">

  <!-- Phase 1: Data query conversation -->
  <ng-container *ngIf="phase === 'query'">
    <h3>Describe your data</h3>
    <div class="messages">
      <div *ngFor="let msg of queryMessages" [class]="'message ' + msg.role">
        <strong>{{ msg.role === 'user' ? 'You' : 'Assistant' }}:</strong> {{ msg.text }}
      </div>
      <div *ngIf="loading" class="message assistant"><em>Thinking…</em></div>
    </div>
    <div class="input-row">
      <input [(ngModel)]="inputText" placeholder="Describe what data you want…"
             [disabled]="loading" (keydown.enter)="send()" />
      <button (click)="send()" [disabled]="loading || !inputText.trim()">Send</button>
    </div>
  </ng-container>

  <!-- Phase 2: Chart appearance conversation -->
  <ng-container *ngIf="phase === 'options'">
    <h3>Describe how you'd like the chart to look</h3>
    <p class="hint">Query ready: <em>{{ queryJson?.nl }}</em></p>
    <p *ngIf="queryDescription" class="hint">Description: <em>{{ queryDescription }}</em></p>
    <div class="messages">
      <div *ngFor="let msg of optionsMessages" [class]="'message ' + msg.role">
        <strong>{{ msg.role === 'user' ? 'You' : 'Assistant' }}:</strong> {{ msg.text }}
      </div>
      <div *ngIf="loading" class="message assistant"><em>Thinking…</em></div>
    </div>
    <div class="input-row">
      <input [(ngModel)]="inputText" placeholder="Describe chart appearance…"
             [disabled]="loading" (keydown.enter)="send()" />
      <button (click)="send()" [disabled]="loading || !inputText.trim()">Send</button>
      <button (click)="skipOptions()" [disabled]="loading">Skip / Use defaults</button>
    </div>
  </ng-container>

  <!-- Phase 3: Chart output -->
  <div *ngIf="chartData" class="chart-area">
    <!-- Replace this with your real chart rendering component, e.g.:
         <app-highcharts-chart [data]="chartData"></app-highcharts-chart> -->
    <pre>{{ chartData | json }}</pre>
  </div>

</div>
```

---

## 4. Register the module

Make sure `HttpClientModule` and `FormsModule` are imported in your `AppModule`
(or standalone component imports):

```typescript
// app.module.ts
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@NgModule({
  imports: [
    // ... existing imports ...
    HttpClientModule,
    FormsModule,
  ],
})
export class AppModule {}
```

---

## 5. Typical conversation flows

### Data query

| Turn | User says | `done` | `sig` |
|------|-----------|--------|-------|
| 1 | "Show me publications per year" | false | — |
| 2 | "Open access only" (agent asked for a filter) | false | — |
| 3 | "Yes, that's correct" (agent confirms) | **true** | `<hmac>` |

### Chart options

| Turn | User says | `done` | `sig` |
|------|-----------|--------|-------|
| 1 | "Blue bars, red title" | false | — |
| 2 | "Move the legend to the right" | false | — |
| 3 | "Looks good, sign it" | **true** | `<hmac>` |

---

## 6. Storing and reusing completed queries and options

`queryJson` and `optionsElement` are self-contained signed objects. Persist them as-is
and replay them directly — no reassembly required:

```typescript
// Save when done — store the ready-to-use objects directly
if (res.done && res.queryJson) {
  localStorage.setItem('lastNlQuery', JSON.stringify(res.queryJson));
}
if (res.done && res.optionsElement) {
  localStorage.setItem('lastChartOptions', JSON.stringify(res.optionsElement));
}

// Later — replay both directly
const q: NlQuery = JSON.parse(localStorage.getItem('lastNlQuery')!);
const o = JSON.parse(localStorage.getItem('lastChartOptions') ?? 'null');

this.nlChat.fetchChart({
  library: 'HighCharts',
  chartsInfo: [{ type: 'bar', query: q }],
  ...(o ?? {}),   // spread optionsElement (nlOptions + optionsSig) if present
}).subscribe(data => { /* render */ });
```

This is equivalent to bookmarking a fully-styled chart: the backend verifies both HMACs, looks everything up in cache, and returns chart data + options without any round-trip to Claude.

---

## 7. Chart request body reference

The `POST /chart` body accepts a mix of NL and traditional DSL queries in the same request. The `query` object inside each `chartsInfo` entry uses one of three forms:

```jsonc
// Form 1 — NL query (new)
{
  "nl": "Number of open access publications per year",
  "sig": "<hmac>",
  "profile": "openaire_stats"
}

// Form 2 — named query (existing, unchanged)
{
  "name": "publications.per_year"
}

// Form 3 — JSON DSL (existing, unchanged)
{
  "entity": "publication",
  "select": [...],
  "filters": [...]
}
```

To include AI-generated chart options, add `nlOptions` and `optionsSig` at the top level:

```jsonc
{
  "library": "HighCharts",
  "chartsInfo": [{ "type": "bar", "query": { "nl": "...", "sig": "...", "profile": "..." } }],
  "nlOptions": "blue bars, red title, legend on the right",
  "optionsSig": "<hmac>"
}
```

The backend dispatches each chart independently based on which fields are present
(`nl+sig` → NL path, `name` → named query, otherwise → DSL). DSL and named queries
continue to be batched together into a single SQL statement for efficiency.

After chart data is collected, if `nlOptions` + `optionsSig` are both present, the backend
verifies the options signature, retrieves the options JSON from cache, and adds it as
`chartOptions` in the response:

```json
{
  "series": [...],
  "chartOptions": {
    "colors": ["#003399"],
    "title": { "text": "My Chart", "style": { "color": "red" } },
    "legend": { "align": "right" }
  }
}
```

---

## 8. Loading query metadata from a URL

When a user loads a saved chart URL that contains an NL query, you can show a read-only "Query info" panel with both the canonical NL string and the generated SQL. Use `GET /nl/info` — it is a pure cache read with no LLM call:

```typescript
loadQueryInfo(nl: string, sig: string, profile: string): void {
  this.nlChat.nlInfo(profile, nl, sig).subscribe({
    next: (info) => {
      this.queryDescription = info.description;  // show in UI
      console.log('Generated SQL:', info.sql);
    },
    error: (err) => {
      if (err.status === 404) {
        // Not cached — execute the chart first (/chart POST), then retry
      } else if (err.status === 403) {
        console.error('Signature invalid');
      }
    }
  });
}
```

If the query has dashboard-injected filters, pass them too:

```typescript
this.nlChat.nlInfo(profile, nl, sig, filters).subscribe(...);
```

> **Important:** `GET /nl/info` only returns data if the query has been executed at least once since the last cache flush. If you get a `404`, execute the chart first by posting to `/chart`, then call `/nl/info` again.

---

## 9. Error handling tips

| Situation | What to do |
|-----------|-----------|
| HTTP 400 from `/nl/chat` | `profile` or `message` is missing — check your request |
| HTTP 400 from `/nl/options/chat` | `library` or `message` is missing — check your request |
| HTTP 403 from `/chart` | Signature is invalid or tampered — do not retry; restart the conversation |
| HTTP 500 | Backend / Claude error — show a generic retry message |
| `done: true` but no `sig` | Should not happen; log and treat as an error |
| User refreshes the page | `sessionId` is lost — start a new conversation (send without `sessionId`) |

---

## 10. Minimal CSS to get started

```css
/* nl-chat.component.css */
.nl-chat { display: flex; flex-direction: column; gap: 1rem; max-width: 640px; }
.messages { display: flex; flex-direction: column; gap: 0.5rem; }
.message { padding: 0.5rem 0.75rem; border-radius: 6px; }
.message.user      { background: #e8f0fe; align-self: flex-end; }
.message.assistant { background: #f1f3f4; align-self: flex-start; }
.hint { color: #555; font-style: italic; font-size: 0.9rem; }
.input-row { display: flex; gap: 0.5rem; }
.input-row input  { flex: 1; padding: 0.5rem; }
.input-row button { padding: 0.5rem 1rem; }
.chart-area { border: 1px dashed #ccc; padding: 1rem; border-radius: 6px; }
```

---

## Summary

1. `POST /nl/chat` with `{ profile, message }` (no `sessionId` on the first message).
2. Echo the returned `sessionId` in every follow-up message.
3. Keep looping until `done: true`. On completion, store `queryJson` (the ready-to-embed query object) and `description` (for display).
4. (Optional) `POST /nl/options/chat` with `{ library, message }` to iteratively design chart appearance. On `done: true`, store `optionsElement` (the ready-to-spread `{ nlOptions, optionsSig }` object).
5. When done, `POST /chart` with:
   - `query: queryJson` inside `chartsInfo` for the data (drop it in directly)
   - `...optionsElement` spread at the top level for appearance (omit if skipping options)
6. (Loading from URL) Call `GET /nl/info?profile=…&nl=…&sig=…` to retrieve `{ sql, description }` for read-only display without executing the query.
