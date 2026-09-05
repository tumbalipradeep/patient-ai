# Release Audit

## Scope inspected

The full Spring Boot MVC application was inspected: controllers, services, repositories,
entities, DTO validation, Spring Security, exception handling, Thymeleaf templates,
JavaScript, CSS, Flyway migrations, application profiles, Docker files, and the complete
automated test suite. Key user workflows checked in code and tests include authentication,
dashboard, patients, cases, encounters and case-taking, appointments, documents, AI intake
draft review/application/discard, admin user management, password changes, audit logging, and
the health endpoint.

## Problems found and fixed

- Fixed lazy-association view failures while `spring.jpa.open-in-view=false`: explicit fetch
  plans now provide patient/clinician data for case detail, encounter lists, document lists,
  and dashboard cards.
- Restored CSRF protection for the state-changing, session-authenticated AI chat endpoint.
  The browser already sends the server-rendered CSRF header; a test now verifies omission is
  rejected before any state can change.
- Rejected mismatched case path/body values when creating an encounter, and prohibited
  assignment of encounters to disabled or non-clinician users.
- Bound follow-up status updates to their parent encounter, preventing a cross-encounter IDOR.
- Validated document references before writing a file, rejected unknown or inconsistent
  patient/case/encounter combinations, and normalized storage paths to absolute paths before
  containment checks.
- Returned form feedback rather than a 500 response for invalid appointment clinician/status
  input.

AI safeguards were preserved: the application retains explicit clinician review/apply,
disallows AI diagnoses, treatments, examinations, vitals, and clinical impressions, and does
not expose provider credentials.

## Verification executed

```text
mvn clean test
Result: SUCCESS — 219 tests, 0 failures, 0 errors, 0 skipped.

git diff --check
Result: SUCCESS — no whitespace errors.

node --check src/main/resources/static/js/*.js
Result: not run; Node.js is not installed in this environment.

docker compose config --quiet
Result: not run; Docker is not installed in this environment.
```

```text
mvn clean package -DskipTests
Result: SUCCESS — executable artifact created at
target/ai-patient-case-system-1.0.0.jar.
```

## Remaining limitations

- Browser-level and Docker Compose runtime smoke tests could not be executed here because
  Node.js and Docker are unavailable. Server-rendered route behavior is covered by the Spring
  MVC/security tests and application-context tests.
- Production still requires externally supplied database credentials and document storage path,
  as designed by `application-prod.yml`.
