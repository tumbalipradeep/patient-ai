# SIH26047 — MediKiosk Engineering Coverage Report

Companion product to the AI Patient Case-Taking System (Spring Boot 3.3.5, Java 21,
PostgreSQL 17, Thymeleaf, Spring Security 6). This report maps the deliverables to the
problem statement's feature areas **A–F** and records the verification performed.

## Verification baseline

| Item | Result |
|------|--------|
| Full automated test suite | **249 tests, 0 failures, 0 errors, 0 skipped** (219 pre-existing baseline + 30 kiosk tests) |
| Compilation | `mvn -q compile` and `mvn -q test-compile` clean |
| Migration V6 on real PostgreSQL 17.11 | Applied successfully on a database already at V1–V5 (`Successfully applied 1 migration`, now at v6). New tables/columns present: `consents`, `kiosk_intakes`, `red_flags`, `ayush_assessments`, `document_extractions`, `patients.user_id`, `users.role` check now includes `PATIENT` |
| Live end-to-end smoke (HTTP + Postgres) | See "Live smoke" below |
| `git diff --check` | Clean |

## Live smoke (real server, real database, no mocks)

Performed against the packaged jar on `localhost:8180` with the `patientcase` PostgreSQL
database and `AI_ENABLED=false` (default):

```
GET  /kiosk/register             200   (public)
POST /kiosk/register             302 -> /kiosk/consent   (auto-sign-in)
GET  /kiosk/consent              200   (authenticated; follows the redirect)
GET  /kiosk/home                 200   (patient portal renders; staff/logged-out are blocked)
POST /kiosk/consent              302 -> /kiosk/intake/1  (consent GRANTED)
GET  /kiosk/intake/1             200   (conversational intake UI)
POST /api/kiosk/chat             {"reply":"AI assistance is not configured...","disabled":true,...}
GET  /kiosk/intake/1/summary     200
```

Database rows after the run confirmed persistence of the whole path:

- `users`: `asha.k`/`PATIENT`, `enabled=true`, `must_change_password=false`
- `patients`: linked `user_id`, number `P-001009` from `patient_number_seq`
- `consents`: `patient_intake` / `medikiosk-1.0` / `GRANTED`
- `kiosk_intakes`: `IN_PROGRESS`, `language = en`, consent bound, `messages_json` length > 0 (turn persisted)
- `audit_logs`: `PATIENT_CREATED` entries with client IP

### Defect found & fixed during smoke testing

**Registration auto-login did not survive to the next request.** The post-registration
`authenticate()` set the `SecurityContext` on the thread-local but never persisted it to
the session (Spring Security 6 `SecurityContextHolderFilter` only saves the context it
loaded). Symptom: `POST /kiosk/register` returned `302 -> /kiosk/consent`, but the follow-up
`GET /kiosk/consent` bounced to `/login`.

Fix: persist the freshly authenticated context via `SecurityContextRepository.saveContext(...)`
(added `securityContextRepository()` bean in `SecurityConfig`, wired into `KioskController`),
plus a regression test `KioskFlowTest.register_post_autoLoginThenConsentAccessible` that
replays the register POST and reuses the session to assert `/kiosk/consent` is reachable.

## Coverage areas

### A. Digital-first patient journey (registration → consent → portal) — COMPLETE
- Self-registration at `/kiosk/register` with server-side validation; creates `User`
  (`ROLE_PATIENT`, `enabled`, `must_change_password=false`, BCrypt(12)) and a linked
  `Patient` atomically, numbered via `patient_number_seq`, audited `PATIENT_CREATED`.
- Auto sign-in after registration (verified above); failed auto-login degrades to the
  normal login page.
- Language landing at `/kiosk` (English active, Hindi active, Marathi marked "Soon" —
  no fabricated translations; UI language stored per intake in `kiosk_intakes.language`).
- Patient portal `/kiosk/home`: active-intake progress, consent state, intake history.

### B. AI-assisted, patient-report-only clinical history taking — COMPLETE (AI must be provisioned)
- Conversational intake UI at `/kiosk/intake/{id}` backed by `POST /api/kiosk/chat`.
- Server-authoritative conversation history (persisted `messages_json`; the client can
  never inject history). A synthetic, non-persisted system context note is prepended.
- Reuses the existing `AiChatService` + `AiDraftValidator`; on completion the validated
  structured draft is persisted (`KioskIntakeStatus.DRAFT_READY`) with red flags.
- Hard safety boundary: prohibited fields (diagnoses, treatments, exams, vitals, etc.)
  are rejected by the draft validator; only patient-reported data reaches clinicians.
- Graceful when the AI provider is absent (`disabled: true`, no error, no secrets);
  provider binding is via `AI_ENABLED`/`AI_API_KEY` (never returned to the browser).

### C. AYUSH/integrative assessment capture — COMPLETE
- Dashavidha Pariksha form at `/kiosk/intake/{id}/ayush` (prakriti, vikriti, sara,
  samhanana, pramana, satmya, satva, ahara-shakti, vyayama-shakti, vaya, ahara/vihara
  details, notes) persisted via `ayush_assessments`.
- Presented alongside the modern clinical draft and red flags in the physician review.

### D. Document digitization (prior records) — COMPLETE (provider-bound, honest fallback)
- Upload at `/kiosk/intake/{id}/documents` using the existing `DocumentService`.
- Digitization pipeline via `OcrExtractionService` + pluggable `OcrProvider`;
  unavailable providers return `UNSUPPORTED` — extraction is **never fabricated**.
- Extraction status per document shown to patients and clinicians
  (`document_extractions`).

### E. Clinician workflow (queue, accept, reject) — COMPLETE
- Review queue `/intakes` (role: ADMIN/DOCTOR/NURSE) with urgency and red-flag badges.
- Review detail `/intakes/{id}`: patient info, status/consent, red flags, AYUSH, draft,
  symptoms, documents + extraction status.
- Accept creates a real `PatientCase` + `Encounter` (DRAFT, clinician assigned) using
  `case_number_seq`, with priority derived from urgent red flags (URGENT/HIGH);
  redirect to the encounter. Reject marks the intake rejected.
- Dashboard shows a pending-intake banner for clinical staff.

### F. Security, privacy & auditability — COMPLETE
- Route-level RBAC: public `/kiosk*`/`/login`/static; `/kiosk/**`+`/api/kiosk/**`
  require `PATIENT`; `/intakes/**` requires ADMIN/DOCTOR/NURSE; all routes role-checked.
- Patient data can only be accessed at the patient's own `user_id` owner boundary
  (non-owned intake → 404, not 403, to avoid oracle leaks).
- Consent enforcement: chat and submit paths require granted consent; intake links a
  consent record (versioned `medikiosk-1.0`).
- FHIR/ABDM boundary: local R4 projections only, `app.integration.mode` disabled by
  default; no live credentials anywhere.
- CSRF protection on state-changing form routes; JSON API CSRF header honored;
  chat client renders AI text with `textContent` only (XSS-safe).
- Audit logging on patient creation, intakes, red flags, consent, accept/reject;
  no secrets logged or returned.

## Honest limitations / operator notes
- The AI conversation requires provisioning `AI_ENABLED=true` + `AI_API_KEY`; without it
  the chat reports it is disabled (by design — never faked).
- OCR results depend on an available `OcrProvider`; the shipped provider is
  `UNSUPPORTED`-fallback only.
- FHIR/ABDM publishing is local R4 projection scaffolding, not live transmission.
- UI copy is English-first; non-English landing labels are true translations, Marathi
  entry is explicitly "Soon" rather than fabricated.