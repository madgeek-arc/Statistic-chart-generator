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
Claude is satisfied → backend returns  done: true  +  canonicalNl + sig
        │
        ▼
(Optional) User describes chart appearance
        │
        ▼
Angular sends POST /nl/options/chat
        │  ← may repeat several turns
        ▼
Claude is satisfied → backend returns  done: true  +  canonicalDescription + sig + optionsJson
        │
        ▼
Angular sends POST /stats-api/chart  (with query.nl + query.sig + nlOptions + optionsSig)
        │
        ▼
Angular renders the chart (HighCharts / GoogleCharts / eCharts)
```

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
  canonicalNl?: string; // populated when done — human-readable summary of the query
  sig?: string;         // populated when done — HMAC signature authorising the query
  sql?: string;         // populated when done — the generated SQL (informational)
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
  canonicalDescription?: string; // populated when done — concise appearance description
  sig?: string;                  // populated when done — HMAC signature
  optionsJson?: string;          // populated when done — chart options JSON string
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
import { NlChatService, ChatResponse, OptionsResponse } from './nl-chat.service';

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
  canonicalNl?: string;
  querySig?: string;

  // Options conversation state
  optionsMessages: Message[] = [];
  optionsSessionId?: string;
  canonicalDescription?: string;
  optionsSig?: string;

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

        if (res.done && res.canonicalNl && res.sig) {
          this.canonicalNl = res.canonicalNl;
          this.querySig = res.sig;
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

        if (res.done && res.canonicalDescription && res.sig) {
          this.canonicalDescription = res.canonicalDescription;
          this.optionsSig = res.sig;
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
        name: this.canonicalNl,
        query: {
          nl: this.canonicalNl!,
          sig: this.querySig!,
          profile: this.profile,
        }
      }],
      // Include options only if the user went through the options conversation
      nlOptions: this.canonicalDescription,
      optionsSig: this.optionsSig,
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
    <p class="hint">Query ready: <em>{{ canonicalNl }}</em></p>
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

The `canonicalNl` + `sig` + `profile` triple is a self-contained signed query.
The `canonicalDescription` + `optionsSig` pair is a self-contained signed options reference.
Both can be persisted and replayed later without calling Claude again:

```typescript
// Save both when done
if (res.done && res.canonicalNl && res.sig) {
  localStorage.setItem('lastNlQuery', JSON.stringify({
    nl: res.canonicalNl, sig: res.sig, profile: this.profile
  }));
}
if (optsDone && res.canonicalDescription && res.sig) {
  localStorage.setItem('lastChartOptions', JSON.stringify({
    nlOptions: res.canonicalDescription, optionsSig: res.sig
  }));
}

// Later — replay both directly
const q = JSON.parse(localStorage.getItem('lastNlQuery')!);
const o = JSON.parse(localStorage.getItem('lastChartOptions') ?? 'null');

this.nlChat.fetchChart({
  library: 'HighCharts',
  chartsInfo: [{ type: 'bar', query: q }],
  ...(o ?? {}),   // spread nlOptions + optionsSig if present
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

## 8. Error handling tips

| Situation | What to do |
|-----------|-----------|
| HTTP 400 from `/nl/chat` | `profile` or `message` is missing — check your request |
| HTTP 400 from `/nl/options/chat` | `library` or `message` is missing — check your request |
| HTTP 403 from `/chart` | Signature is invalid or tampered — do not retry; restart the conversation |
| HTTP 500 | Backend / Claude error — show a generic retry message |
| `done: true` but no `sig` | Should not happen; log and treat as an error |
| User refreshes the page | `sessionId` is lost — start a new conversation (send without `sessionId`) |

---

## 9. Minimal CSS to get started

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
3. Keep looping until `done: true`.
4. (Optional) `POST /nl/options/chat` with `{ library, message }` to iteratively design chart appearance.
5. When done, `POST /chart` with:
   - `query: { nl, sig, profile }` inside `chartsInfo` for the data
   - `nlOptions` + `optionsSig` at the top level for appearance (omit if skipping options)
