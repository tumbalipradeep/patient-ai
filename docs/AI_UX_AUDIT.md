# AI + UX Engineering Audit — MediKiosk Clinical History Assistant

Audit date: 2026-09-06
Baseline commit: `fa83be3` (tag `medikiosk-qa-passed`); all changes are uncommitted work-in-progress.

## 1. What this audit covers

A dedicated engineering pass on the patient-facing AI conversation experience
("MediKiosk Clinical History Assistant") against 31 explicit requirements and
test scenarios A–O. It does **not** claim the product is fully functional.

Honesty rule applied throughout: every claim below is tagged

- **IMPLEMENTED** — code exists and is covered by automated tests;
- **VERIFIED LIVE** — confirmed against the running application over HTTP;
- **NOT VERIFIED** — cannot be exercised in this environment (no real AI
  provider key), so it is implemented but unproven live.

## 2. Old AI UX — problems found (what we fixed)

| # | Problem in previous chat | Evidence |
|---|---|---|
| 1 | Generic ChatGPT-style textbox; "Optional: ask the AI assistant", multiple "answers" parallel to the guided form | `kiosk/intake.html` old panel; `kiosk-intake.js` |
| 2 | Hard-coded greeting bubble, plain replies, no sections, no progress | old `kiosk-intake.js` |
| 3 | "Clear" button only emptied the DOM — the server conversation was untouched, so the panel and server history disagreed on refresh | old `#btn-clear` handler |
| 4 | No retry of a timed-out turn | no retry UI |
| 5 | No duplicate-submit protection at the API level; a double POST double-appended the conversation and called the provider twice | `appendMessages` unconditional |
| 6 | Refresh / back / forward lost the visible conversation (client-only rendering) | no server-rendered history |
| 7 | The turn protocol already emitted `patientReportedFacts` / `inferredInformation` / `missingInformation` but these were **dropped** at the response boundary — the patient could never see "what we heard" | `AiChatResponse` DTO lacked the fields |
| 8 | No chips / suggested answers; free-text `rows=2` only | old panel |
| 9 | No section concept or progress indication anywhere | — |

## 3. Design (frozen in this pass)

- **One clinical question at a time.** The patient sees the assistant's latest
  question as the prominent bubble; the answer box is `rows=3`.
- **Server is the source of truth.** Conversation history lives in
  `kiosk_intakes.messages_json`. The intake page renders that history
  server-side (`aiHistory`), so refresh, back/forward and reconnection
  reproduce the exact conversation and the last assistant question.
- **Idempotent turns.** The browser sends a `clientTurnId`. A duplicate submit
  is answered from `kiosk_intakes.last_assistant_reply` — no provider call, no
  double append. This covers timeouts (the browser believed the POST failed).
- **Retry that cannot duplicate.** After a transient provider failure the UI
  shows Retry, which re-sends the **same** `clientTurnId` and **same** text.
- **Real "Start over".** `POST /api/kiosk/chat/reset` clears the server-side
  conversation (IN_PROGRESS + consent + ownership guards only); the client then
  reloads, so browser and server never disagree.
- **Richer turn JSON.** `AiChatResponse` now carries `patientReportedFacts`,
  `inferredInformation`, `suggestedAnswers` (≤4, ≤60 chars), `allowOtherText`,
  a normalised `section`, and `sectionProgress` (0–100, clamped). All fields are
  OPTIONAL and backward-compatible with the clinician flow (`ai-intake.js`).
- **Never fabricate.** `inferredInformation` stays empty unless the model
  returned a non-clinical observation; anything shown as inferred is rendered
  with an explicit "please confirm" treatment, visually distinct from
  patient-reported facts. Red-flag alerts carry an explicit "not a diagnosis —
  a clinician will review" disclaimer.
- **Guided intake remains the reliable primary path** when AI is disabled,
  and is always present even when AI is on.

## 4. Backend changes

| File | Change |
|---|---|
| `db/migration/V8__kiosk_ai_conversation_idempotency.sql` | `last_client_turn_id VARCHAR(64)` + `last_assistant_reply TEXT` on `kiosk_intakes` |
| `KioskIntake.java` | entity fields + accessors |
| `AiChatResponse.java` | new optional conversational fields, `withConversation(...)`, `retryable()`, `retryable` flag; `NON_NULL` JSON |
| `AiService.java` | SYSTEM_PROMPT turn schema extended (+SECTION GUIDANCE); `TurnResponse` gains `section`/`sectionProgress`/`suggestedAnswers`/`allowsOtherText`; `buildResponse` sanitises/bounds/clamps and populates the new fields; `RestClientException` (provider/timeout) now returns a **retryable** error |
| `KioskChatRequest.java` | optional `clientTurnId` (≤64) |
| `KioskApiController.java` | duplicate-turn short-circuit (uses `isKnownTurn`/`storedReplyForTurn`), `recordTurn` persistence, `POST /chat/reset` with PATIENT/ownership/consent/IN_PROGRESS guards |
| `KioskIntakeService.java` | `recordTurn`, `storedReplyForTurn`, `isKnownTurn`, `resetConversation`, `getConversationMessages` |
| `KioskIntakeService.java` | (existing) `appendMessages` kept for compatibility |
| `KioskController.java` | `aiHistory` model attribute (server conversation) when `aiEnabled` and IN_PROGRESS |

No secrets, provider endpoints or stack traces are returned or logged; patient
conversation content is never logged.

## 5. Frontend changes

`kiosk/intake.html` — the AI card became the MediKiosk Clinical History
Assistant:

| Markup element (`intake.html`) | Behaviour (kiosk-intake.js) | Verified by |
|---|---|---|
| `#ai-panel` (338, `th:if`) | only rendered when `aiEnabled` | MockMvc + live (disabled-mode absence) |
| `#btn-reset` (344) | `startOver()` → confirm → `POST /api/kiosk/chat/reset` → reload (477) | MockMvc + live (disabled-mode) |
| `#ai-section-label`/`#ai-progress-bar` (352/360) | `applySection()` shows friendly section name + clamped 0–100 (354) | unit (clamp), MockMvc render |
| `#chat-messages` (364) | server-rendered `aiHistory` + greeting only when empty; JS appends new bubbles | MockMvc + live (guided) |
| `#chat-chips` (417) | `renderChips()` builds ≤4 buttons + "Other" when allowed (329) | unit (bounding), markup |
| `#facts-panel`/`#inferred-panel` (421/427) | `renderFacts()` — patient-reported vs "confirm" inferred (365) | markup |
| `#chat-loading` (435) | typing indicator while busy | markup |
| `#chat-error` (443) + `#btn-retry` (449) | `showError(msg, retryable)`; Retry re-sends same `clientTurnId`+text (309) | MockMvc (`retryable`) |
| `#chat-input` rows=3 (466) | Enter sends, Shift+Enter newline, Ctrl/Cmd+Enter sends; 0/4000 counter; `maxlength=4000` | markup |
| `#btn-send` (472) | disabled while busy; blank text never sent | markup + MockMvc (`empty->400`) |
| `#ai-complete-card` (309) | shown on `complete` → review/summary / ayush / documents | MockMvc (`complete`) |
| `kiosk-intake.js` loaded only `th:if ${aiEnabled}` (503) | state machine IDLE→SENDING | live (absent when disabled) |

Rendering hygiene: every dynamically-built string uses `textContent` /
`createElement` — no `innerHTML` with data. The previous `onclick` inline
handler was removed (dismiss button is now bound in JS).

## 6. Broken down by the original "generic chat UI" complaints

- "Generic ChatGPT textbox" → one-question-at-a-time assistant, chips + Other,
  section + progress, facts panel, retry/reset, auto-scroll.
- "Server and browser disagree" → server-rendered history + real server reset
  + idempotent duplicate handling.

## 7. Test-scenario matrix A–O

| # | Scenario | Implementation | Coverage | Status |
|---|---|---|---|---|
| A | AI enabled + valid provider | full turn flow (system prompt → parse → enrich → persist) | unit `testableParseReply` + MockMvc (mocked provider) | IMPLEMENTED / NOT VERIFIED (no real key) |
| B | AI disabled | `th:if` panel, AI-off notice, guided wizard primary | MockMvc `intakePage_aiDisabled...` + live 25-check | IMPLEMENTED / VERIFIED LIVE |
| C | Provider failure | `AiChatResponse.error(...).retryable()` + Retry UI | MockMvc `chat_providerError_isRetryable`, `chat_retryAfterError...` | IMPLEMENTED |
| D | Provider timeout | `RestClientException` → retryable error | same path as C (RestTemplate timeouts unchanged) | IMPLEMENTED / NOT VERIFIED (no provider) |
| E | Malformed response | `parseReply` fallback errors; missing nextQuestion → error | unit `parseReply_missingNextQuestion_returnsError` (existing) | IMPLEMENTED / NOT VERIFIED |
| F | Empty answer | server 400 + client trim/guard | MockMvc `chat_emptyMessage_returnsBadRequest` (existing) + live `empty message -> 400` | IMPLEMENTED / VERIFIED LIVE |
| G | Duplicate Send | disables Send while busy + `clientTurnId` idempotency | MockMvc `chat_sameClientTurnIdTwice_callsProviderOnlyOnce`, `chat_differentTurnIds...` | IMPLEMENTED |
| H | Refresh | server-rendered `aiHistory` + last question | MockMvc `intakePage_aiEnabled_withHistory_rendersServerConversation` | IMPLEMENTED |
| I | Back/forward | same server-rendered page | same as H (server render) | IMPLEMENTED |
| J | Unauthorized patient | PATIENT-only `/api/kiosk/**`, ownership check | MockMvc `reset_nonOwnedIntake_isNotFound`, existing `chat_nonOwnedIntake_returnsNotFound`, `chat_asStaff_isForbidden` | IMPLEMENTED / VERIFIED LIVE (staff 403 in 25-check) |
| K | Expired session | 401 handling + login redirect | existing `chat_unauthenticated_redirectsToLogin` | IMPLEMENTED / VERIFIED LIVE (25-check) |
| L | Consent revoked / absent | chat + reset both require consent | MockMvc `reset_withoutConsent_isForbidden`, existing `chat_asPatient_withoutConsent...` + live | IMPLEMENTED / VERIFIED LIVE |
| M | Red-flag response | alert banner + urgent variant + "not a diagnosis" | existing red-flag tests + MockMvc complete-draft test | IMPLEMENTED |
| N | Long response | reply bubble `white-space:pre-wrap`, max auto-scroll | markup | IMPLEMENTED / NOT VERIFIED (no provider) |
| O | Mobile viewport | Bootstrap flex layout, big touch targets, chips wrap | static audit (no browser emulation in this env) | IMPLEMENTED / markup-verified |

## 8. Automated test evidence

Full suite: **292 run, 0 failures** (was 277 before this pass).

New tests:
- `AiBatch4IntelligenceTest` (+5): conversational enrichment fields, unknown
  section normalisation, suggested-answer bounding/truncation, complete-turn
  progress=100 / allowOther=false, progress clamping.
- `KioskAiConversationFlowTest` (new class, +10): AI-enabled panel render,
  server-conversation render with history, duplicate-turn provider-once +
  history-not-doubled, distinct turns append, retryable error surface,
  retry-after-error records once, reset clears history, reset without consent
  forbidden, reset at DRAFT_READY conflict, reset on foreign intake 404.

## 9. Live verification (running app, AI disabled)

`/tmp/opencode/live-aiux.sh` — **13/13 PASS** after restart with V8 migration:

login ok, active intake, consent granted, AI panel absent when disabled,
AI-off notice shown, guided wizard present, `kiosk-intake.js` not loaded when
disabled, csrf meta present, `POST /api/kiosk/chat` → `disabled:true`,
empty message → 400, reset → 200, guided post → summary redirect, summary
DRAFT_READY page renders.

Also re-run of the 25-point functional harness remains green (register →
consent → guided wizard → DRAFT_READY → summary → physician review).

## 10. Honest limitations and NOT VERIFIED items

1. No real AI provider key in this environment → scenarios A, D, E, N, and the
   live chip rendering are **IMPLEMENTED but NOT VERIFIED on a real provider**.
   When a key is set (`app.ai.enabled=true` + `app.ai.api-key`), the enriched
   turn schema in the system prompt will be exercised for the first time.
2. After a page refresh, chips for the in-flight question are not restored
   (only the question text is). Free-text answer still works; the next turn
   re-sends chips.
3. Browser-side behaviours (chip tap, Retry click, resize, keyboard) are
   verified by automation only at the API/render boundary; no browser/JS
   engine automation exists in this environment — interactions are
   **markup-verified**, not mouse-click-verified.
4. Section labels are client-side; the server only guarantees the safe token
   set (unknown → `OTHER`), so a future UI re-skin needs only the label map.
5. Reset clears the conversation but never a previously persisted draft; a
   DRAFT_READY intake is not resettable (conflict) — intentional.

## 11. Constraints honoured

- No git commit or push.
- Logo untouched.
- No framework or dependency version changes.
- The real AI provider is used whenever configured; disabled mode is the
  guided wizard; nothing is faked.
- Product is not declared fully functional anywhere in this audit.