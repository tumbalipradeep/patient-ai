# MediKiosk — Complete Functionality Audit (Guided Intake Phase)

**Project:** AI Patient Case-Taking System (MediKiosk)
**Audit date:** 2026-09-06
**Mode:** continuation of the prior QA report — fixes limited to genuine verified defects with minimal safe changes and regression tests. No feature work beyond the Guided-Intake design decision, no dependency upgrades, no pushes/deploys/commits.
**Local app:** running on `http://localhost:8080` (restarted with the Guided-Intake build; `SERVER_PORT:8080`, PostgreSQL local)
**Baseline reference:** git tag `pre-medikiosk-build`; working reference `fa83be3` (tag `medikiosk-qa-passed`)
**Supersedes:** `docs/COMPLETE_QA_REPORT.md` (its scope is fully re-verified here on the new build)

Result statuses: **VERIFIED WORKING**, **VERIFIED WORKING WITH LIMITATION**, **FIXED (with regression test)**, **NOT VERIFIED** (used only where tooling truly forbids it and stated honestly).

---

## 1. Environment & Baseline (re-check on new build)

| Item | Value | Status |
|---|---|---|
| OS / runtime | Linux, Java 25.0.4.1 (project targets Java 21), Maven 3.9.11 | OK |
| Database | PostgreSQL 17.11, db/user `patientcase` | OK |
| Flyway | V1–V7 applied; `validate-on-migrate: true`; no failed migrations on the restarted app | VERIFIED WORKING |
| App startup | `/actuator/health` = UP; `/kiosk` 200; `/login` 200; `/dashboard` → 302 `/login` anonymous | VERIFIED WORKING |
| Full test suite | `mvn -o test` → **277 tests, 0 failures, 0 errors, 0 skipped** (baseline was 265/267; +10 regression tests in this phase) | VERIFIED WORKING |
| Demo accounts | `admin`/`Admin@123`; `dr.smith`, `dr.johnson`, `nurse.jones`, `reception` all `/Doctor@123`; kiosk accounts `kiosk.qa`/`kiosk.qb` `/Kiosk@123` | VERIFIED WORKING |
| Grid hygiene | `git diff --check` clean prior; working tree preserves all uncommitted MediKiosk work | OK |

---

## 2. Design decision: Guided intake becomes the primary kiosk flow

The audit confirmed the kiosk intake page was only usable when the AI provider was reachable (`app.ai.enabled:true` **and** a valid OpenAI key). With AI off that the page still **worked** (chat input posted to `/api/kiosk/chat` → 503/stream-fail handled) but offered no real case-taking path. This is recorded as the one deliberate feature decision of the audit:

- **Decision:** the structured, guided wizard is the PRIMARY intake flow. It requires no AI. It assembles the same validated structural JSON (`AiDraftValidator.serialise`) that the AI pipeline produces, then saves via the unchanged `KioskIntakeService.saveDraft` → the physician review/accept pipeline is identical for guided and AI intakes.
- The AI "chat your symptoms" card is rendered **only** when `aiEnabled` is true, and always shows a professional notice when AI is disabled.

---

## 3. Audit findings: DEFECT → ROOT CAUSE → FIX → TEST → STATUS

### 3.1 Guided intake wizard (UI)
- **DEFECT:** with AI disabled the kiosk intake page had no complete structured input path.
- **ROOT CAUSE:** `kiosk/intake.html` was chat-centric; no fallback.
- **FIX:** rewrote `templates/kiosk/intake.html` as a 4-section wizard (Reason for visit → History of present illness → Background → Safety signals & review) with a progress bar, tap-chips bound to real checkboxes, one question per step; added `static/js/kiosk-guided.js`. Kept the single `#char-count` element `kiosk-intake.js` mutates so the AI client stays NPE-free. Removed unsafe `${{...}}` literals (Thymeleaf parse failure) → option lists moved to `KioskController` constants (`QUICK_CHIEF_COMPLAINTS`, `ASSOCIATED_SYMPTOM_OPTIONS`, `SAFETY_SIGNAL_OPTIONS`) exposed via model attributes.
- **TEST:** `KioskFlowTest.intakePage_aiDisabled_rendersGuidedWizardWithoutChat`; live: intake page shows wizard **and** AI-disabled notice **and** no AI chat panel.
- **STATUS:** FIXED (with regression test).

### 3.2 Guided form backend pipeline
- **DEFECT:** no server endpoint saved a guided submission.
- **ROOT CAUSE:** intake write path assumed AI draft JSON only.
- **FIX:** `KioskController.saveGuidedCase` (`POST /kiosk/intake/{id}/case`) binds `@ModelAttribute("guidedForm")` → `KioskIntakeService.saveGuidedDraft` → `buildGuidedDraft + validator.serialise + saveDraft(intakeId, patientId, json, urgent)` → DRAFT_READY → redirect to `/summary`. IllegalState (missing consent) → flash error + redirect back to `/kiosk/intake/{id}`.
- **TEST:** `KioskIntakeServiceTest` (+4 methods incl. `mapsFormIntoDraftReady`), `KioskFlowTest.guidedCasePost_savesDraftReadyAndRedirectsToSummary`.
- **STATUS:** FIXED (with regression test).

### 3.3 Physical-exam & safety-signal capture
- **DEFECT:** guided flow could not record PE findings or red-flag signals.
- **ROOT CAUSE:** DTO absent.
- **FIX:** nested `GuidedIntakeForm` (all fields incl. `pe`, `findings`, `safetySignals`) + `hasUrgentSignals()` against the `URGENT_SIGNALS` set → urgent flag rides through the same `saveDraft` URGENT path.
- **TEST:** `KioskIntakeServiceTest.guidedForm_urgentSignal_createsUrgentCaseOnAccept`; live: physician review page shows guided red flags.
- **STATUS:** FIXED (with regression test).

### 3.4 Consent enforcement
- **DEFECT:** guided save must not proceed without granted consent.
- **ROOT CAUSE:** none in product (consent gate existed); the new POST path needed the same guard.
- **FIX:** `saveGuidedDraft` validates `intake.getConsent()`; controller handles IllegalStateException with a flash + redirect (400-path avoided).
- **TEST:** `KioskIntakeServiceTest.saveGuidedDraft_withoutConsent_isRejected`; `KioskFlowTest.guidedCasePost_withoutConsent_redirectsBackWithError`.
- **STATUS:** FIXED (with regression test).

### 3.5 Ownership / authorization
- **DEFECT:** a patient must not save into another patient's intake.
- **ROOT CAUSE:** POST path needed the same `requireOwnedIntake` guard that GET already enforced.
- **FIX:** ownership check on the POST; non-owner → `AccessDeniedException` → `GlobalExceptionHandler` → HTTP 403 + `errors/403`.
- **TEST:** `KioskIntakeServiceTest.otherPatientsIntake_isDenied`; `KioskFlowTest.guidedCasePost_nonOwnedIntake_isForbidden`; live: patient GET on foreign intake → 403 template with "Kiosk Home".
- **STATUS:** FIXED (with regression test).

### 3.6 AI-disabled operation & disclaimer
- **DEFECT/FIX:** see Section 2. AI chat card is `th:if="${aiEnabled}"`; the professional AI-off notice is `th:unless`. `KioskController.aiEnabled` reads `@Value("${app.ai.enabled:false}")` (tests run with AI off).
- **TEST:** render tests + live (notice visible, no chat panel).
- **STATUS:** VERIFIED WORKING WITH LIMITATION (real AI chat/OCR requires `AI_ENABLED=true` plus provider secrets; not exercised live by design).

### 3.7 Receptionist Appointments navigation (F3)
- **DEFECT:** `sidebar.html`/`navbar.html` gated the Appointments item to ADMIN/DOCTOR/NURSE, but SecurityConfig permits RECEPTIONIST on `/appointments`.
- **ROOT CAUSE:** template gate narrower than route permission.
- **FIX:** added `RECEPTIONIST` to the nav `sec:authorize` (both fragments).
- **TEST:** live: receptionist dashboard renders Appointments nav; SecurityConfig route-role matrix audited.
- **STATUS:** FIXED.

### 3.8 Receptionist dashboard "Recent Encounters" (F4)
- **DEFECT:** receptionist saw a card linking `/encounters/…` (route is ADMIN/DOCTOR/NURSE only) → click would 403.
- **ROOT CAUSE:** dashboard rendered the card regardless of role.
- **FIX:** `sec:authorize="hasAnyRole('ADMIN','DOCTOR','NURSE')"` on the card.
- **TEST:** `KioskFlowTest.dashboard_asReceptionist_hidesEncounterManagement`; live: no `/encounters` links for receptionist.
- **STATUS:** FIXED (with regression test).

### 3.9 Patient/receptionist case-creation UI (F5)
- **DEFECT:** `patients/profile.html` exposed Create-Case buttons, View link, and `#createCaseModal` to roles whose `/cases/new` route is denied.
- **ROOT CAUSE:** UI not role-gated.
- **FIX:** gated all four elements + the modal with `hasAnyRole('ADMIN','DOCTOR','NURSE')`.
- **TEST:** `KioskFlowTest.profile_asReceptionist_hidesCaseManagementUi`; live (no `/cases/new` for receptionist; no modal).
- **STATUS:** FIXED (with regression test).

### 3.10 Role-aware error pages (F6)
- **DEFECT:** `errors/400|403|404|405|500` always offered "Back to Dashboard", which PATIENT (denied) and anonymous roles cannot open.
- **ROOT CAUSE:** static template links.
- **FIX:** `xmlns:sec` + role-aware home buttons: staff → Dashboard, PATIENT → kiosk home, anonymous → login.
- **TEST:** live: patient 403 → "Kiosk Home" + no "Back to Dashboard"; staff 404 → "Back to Dashboard".
- **STATUS:** FIXED (with documented limitation below: security-filter-level denials on non-kiosk staff URLs are served as Spring Boot JSON 403 by `ExceptionTranslationFilter` and never reach the template; GET `/dashboard` as a patient is one such case. This is pre-existing framework behavior, unchanged.)

### 3.11 CSRF documentation (F7)
- **DEFECT:** `AiController.java` comment claimed `/api/**` was CSRF-exempt.
- **ROOT CAUSE:** stale comment — CSRF is global and `/api/kiosk/chat`, `/api/ai/chat` require `X-CSRF-TOKEN`.
- **FIX:** corrected the comment; no behavior change.
- **TEST:** SecurityConfig audit + existing CSRF tests remain green.
- **STATUS:** FIXED (comment only).

### 3.12 Registration validation (live-harness finding)
- **DEFECT:** (reported against the harness, not the app) live `POST /kiosk/register` returned HTTP 200 with the register page instead of 302.
- **ROOT CAUSE:** the harness generated 9-digit phone numbers (`9$(date +%s | tail -c 9)`) — the DTO pattern `^[0-9+\-\s]{10,15}$` rightly rejected them, and `register.html` renders **no** per-field error text (only `required`) so the re-render looks "silent". App behavior is correct: invalid data → validation error page (200), valid → 302 to consent.
- **FIX:** harness now posts a deterministic ≥10-digit phone (`9555` + epoch).
- **TEST:** `registerPage_carriesCsrfToken` + full live patient flow after fix.
- **STATUS:** FIXED (harness); product validation VERIFIED WORKING.

### 3.13 Summary page state banner
- **DEFECT:** none in product. Live check first expected a generic "gathered so far" banner; `summary.html` correctly has a dedicated `DRAFT_READY` banner ("Verify the information below, then submit for physician review."). Harness marker corrected.
- **TEST:** live.
- **STATUS:** VERIFIED WORKING.

### 3.14 Submit → physician review → accept (guided intakes)
- **DEFECT:** none.
- **FIX:** none required — guided drafts flow through the existing review queue unchanged.
- **TEST:** live: submit → `/kiosk/home`; the intake appears in `/intakes`; review page renders guided chief complaint + red flags; clinician accept → redirect to new encounter page.
- **STATUS:** VERIFIED WORKING.

### 3.15 Urgent red-flag visibility to clinicians
- **DEFECT:** none.
- **FIX:** none required.
- **TEST:** service test (urgent case on accept) + live review page shows guided red flags.
- **STATUS:** VERIFIED WORKING.

### 3.16 Language selector
- **DEFECT:** the picker passes a `lang` param but pages render English only (no translation resources exist).
- **FIX:** none (documented limitation). Verified the passthrough is coherent: `index → login(lang=…)`, login form carries a `lang` hidden field, register↔login links retain the value; `en`/`hi` variants all 200; index shows the Hindi option.
- **TEST:** live (6 checks).
- **STATUS:** VERIFIED WORKING WITH LIMITATION.

### 3.17 Anonymous auth-redirect coverage
- **DEFECT:** none.
- **FIX:** none.
- **TEST:** live — anonymous requests to `/dashboard`, `/appointments`, `/encounters`, `/intakes`, `/cases`, `/admin` all 302 → `/login`.
- **STATUS:** VERIFIED WORKING.

### 3.18 Static resources
- **DEFECT:** none; added `kiosk-guided.js`; kept `kiosk-intake.js` intact; all CSS/JS paths resolve (no 404s, no console errors in render checks).
- **TEST:** render + live.
- **STATUS:** VERIFIED WORKING.

### 3.19 Regression-suite stability
- **DEFECT:** none — this phase added 10 regression tests (baseline 267 → 277) covering the new guided pipeline and the role-gating fixes.
- **TEST:** `mvn -o test` → 277/0/0/0.
- **STATUS:** VERIFIED WORKING.

### 3.20 Runtime hygiene (dev loop)
- **DEFECT:** (dev hygiene) the live app restarted by `nohup … &` was killed when the shell tool reaped the process group.
- **FIX:** relaunch uses `setsid … < /dev/null & disown`; single instance on 8080; log `/tmp/opencode/app.log`.
- **TEST:** explorer/live run of the entire audit against that process.
- **STATUS:** VERIFIED WORKING WITH LIMITATION (dev-loop detail, not product behavior).

---

## 4. Live verification evidence (new build)

Automated harness `/tmp/opencode/live-verify.sh` performing real HTTP against `localhost:8080` (CSRF tokens, cookie jars, redirect capture):

**Patient journey (fresh self-registration per run):** register → auto-login → consent → guided intake (wizard + AI-disabled notice + no chat panel) → guided POST → summary (chief complaint rendered, DRAFT_READY banner) → submit → kiosk home. **PASS.**

**Authorization/RBAC:** patient blocked from `/dashboard` (403); patient 403 template offers "Kiosk Home" and no dashboard link; receptionist dashboard shows Appointments; receptionist sees no `/encounters` links and no case-creation UI/modal on profile. **PASS.**

**Clinician review:** guided intake in review queue; review page 200 with guided red flags; accept → redirect into the new encounter. **PASS.**

**Final run result: 25/25 PASS, 0 FAIL.**

Additional Phase-19 best-effort checks (curl/render, no browser automation available): language-selector coherence (6 checks), anonymous redirect matrix (6 URLs → 302 `/login`), staff 404 template (role-aware "Back to Dashboard"), patient 403 template (role-aware "Kiosk Home"). **All PASS.**

---

## 5. Constraints honored

- No framework/dependency upgrades; committed baseline unchanged (`fa83be3`).
- No hardcoded secrets; AI/OCR/ABDM/FHIR remain stub/flag-gated, never faked as working.
- Preserved existing working functionality; all product edits are template/controller/service/test changes with regression coverage.
- All uncommitted work remains in the working tree (no pushes/deploys/commits made).

---

## 6. Known limitations (stated honestly)

1. **Browser automation is NOT available** in this environment — Phase-19 UI checks are best-effort curl/render checks; visual/interactive (pointer, chips tap, real chat) behavior is covered by unit tests + server-rendered markup only.
2. **Security-filter-level 403s** on non-kiosk staff URLs (e.g. GET `/dashboard` as a patient) are emitted by Spring's `ExceptionTranslationFilter` as JSON and never reach `errors/403`; the template path is used for controller/service-level denials (e.g. foreign kiosk intake). Pre-existing behavior, unchanged.
3. **Real AI/OCR features** are not exercised live (`app.ai.enabled:false`), by design of the audit.
4. **i18n** remains English-only; the selector is a coherent placeholder.
5. The guided flow stores the same structural data the AI flow did; no DB schema change was needed or made.