# Angular Integration Guide — Natural Language Query Agent

This guide shows a beginner-friendly Angular integration for the NL chat API.
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
Angular sends POST /stats-api/chart  (with query.nl + query.sig)
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
}

export interface ChartRequest {
  library: string;      // "HighCharts", "GoogleCharts", or "eCharts"
  chartsInfo: ChartInfo[];
  orderBy?: string;
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
import { NlChatService, ChatResponse } from './nl-chat.service';

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
  messages: Message[] = [];
  inputText = '';
  sessionId?: string;
  loading = false;
  chartData: unknown = null;   // set when chart data is retrieved

  constructor(private nlChat: NlChatService) {}

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.loading) return;

    this.messages.push({ role: 'user', text });
    this.inputText = '';
    this.loading = true;

    this.nlChat.chat({
      sessionId: this.sessionId,   // undefined on first turn — that is intentional
      profile: this.profile,
      message: text,
    }).subscribe({
      next: (res: ChatResponse) => {
        this.sessionId = res.sessionId;   // always keep the latest sessionId
        this.messages.push({ role: 'assistant', text: res.reply });
        this.loading = false;

        if (res.done && res.canonicalNl && res.sig) {
          this.loadChart(res.canonicalNl, res.sig);
        }
      },
      error: (err) => {
        console.error('NL chat error', err);
        this.messages.push({ role: 'assistant', text: 'Sorry, something went wrong.' });
        this.loading = false;
      },
    });
  }

  private loadChart(canonicalNl: string, sig: string): void {
    this.nlChat.fetchChart({
      library: 'HighCharts',
      chartsInfo: [{
        type: 'bar',
        name: canonicalNl,
        query: {
          nl: canonicalNl,
          sig: sig,
          profile: this.profile,
        }
      }]
    }).subscribe({
      next: (data) => {
        // data is the chart JSON (HighCharts/GoogleCharts/eCharts format)
        // Pass it to your existing chart rendering component here
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

  <!-- Message history -->
  <div class="messages">
    <div *ngFor="let msg of messages"
         [class]="'message ' + msg.role">
      <strong>{{ msg.role === 'user' ? 'You' : 'Assistant' }}:</strong>
      {{ msg.text }}
    </div>
    <div *ngIf="loading" class="message assistant">
      <em>Thinking…</em>
    </div>
  </div>

  <!-- Input box — disabled while the agent is replying -->
  <div class="input-row">
    <input
      [(ngModel)]="inputText"
      placeholder="Describe what data you want…"
      [disabled]="loading"
      (keydown.enter)="send()"
    />
    <button (click)="send()" [disabled]="loading || !inputText.trim()">
      Send
    </button>
  </div>

  <!-- Chart placeholder — shown once chart data is ready -->
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

## 5. Typical conversation flow

| Turn | User says | `done` | `sig` |
|------|-----------|--------|-------|
| 1 | "Show me publications per year" | false | — |
| 2 | "Open access only" (agent asked for a filter) | false | — |
| 3 | "Yes, that's correct" (agent confirms) | **true** | `<hmac>` |

Once `done` is `true`:
- Stop showing the input box (or reset the `sessionId` to start a new query).
- Use `canonicalNl` + `sig` to POST to `/chart` and render the result.

---

## 6. Storing and reusing a completed query

The `canonicalNl` + `sig` + `profile` triple is a self-contained signed query.
You can persist it and replay it later without calling Claude again:

```typescript
// Save to localStorage when done
if (res.done && res.canonicalNl && res.sig) {
  const saved = { nl: res.canonicalNl, sig: res.sig, profile: this.profile };
  localStorage.setItem('lastNlQuery', JSON.stringify(saved));
}

// Later, on a chart page — replay it directly
const saved = JSON.parse(localStorage.getItem('lastNlQuery')!);
this.nlChat.fetchChart({
  library: 'HighCharts',
  chartsInfo: [{ type: 'bar', query: saved }]
}).subscribe(data => { /* render */ });
```

This is the equivalent of bookmarking a chart: the backend verifies the HMAC, generates
(and caches) the SQL, and returns the chart data without another round-trip to Claude.

---

## 7. Chart request body reference

The `POST /chart` (or `POST /stats-api/chart`) body accepts a mix of NL and traditional
DSL queries in the same request. The `query` object inside each `chartsInfo` entry uses
one of three forms:

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

The backend dispatches each chart independently based on which fields are present
(`nl+sig` → NL path, `name` → named query, otherwise → DSL). DSL and named queries
continue to be batched together into a single SQL statement for efficiency.

---

## 8. Error handling tips

| Situation | What to do |
|-----------|-----------|
| HTTP 400 from `/nl/chat` | `profile` or `message` is missing — check your request |
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
4. When done, `POST /chart` with `query: { nl, sig, profile }` inside `chartsInfo` to fetch and render the chart.
