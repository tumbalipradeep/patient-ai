# Architecture Documentation

## AI Patient Case-Taking System

---

## Overview

The AI Patient Case-Taking System follows a classic server-side rendered (SSR) MVC architecture using Spring Boot and Thymeleaf. The architecture is intentionally simple and maintainable.

```
Browser
  ↓ HTTP Request
Thymeleaf (Server-Side Rendering)
  ↓ View Names / Model
Spring MVC Controllers
  ↓ DTOs
Services (Business Logic)
  ↓ Entities
Repositories (Spring Data JPA)
  ↓ SQL
PostgreSQL
```

---

## Package Structure

```
com.patientcase
├── PatientCaseApplication         # Spring Boot entry point
├── config/
│   ├── SecurityConfig             # Spring Security configuration
│   ├── GlobalExceptionHandler     # Centralized exception handling
│   └── WebConfig                  # MVC and resource configuration
├── security/
│   └── UserDetailsServiceImpl     # Spring Security UserDetails integration
├── auth/
│   └── AuthController             # Login/logout endpoints
├── user/
│   ├── User                       # Entity
│   ├── Role                       # Enum (ADMIN, DOCTOR, NURSE, RECEPTIONIST)
│   ├── UserRepository
│   └── UserService
├── patient/
│   ├── Patient                    # Entity
│   ├── Gender                     # Enum
│   ├── PatientRepository
│   ├── PatientService
│   ├── PatientController
│   ├── PatientCreateRequest       # DTO
│   └── PatientUpdateRequest       # DTO
├── case_management/
│   ├── PatientCase                # Entity
│   ├── CaseStatus                 # Enum
│   ├── CasePriority               # Enum
│   ├── PatientCaseRepository
│   ├── PatientCaseService
│   ├── PatientCaseController
│   ├── CaseCreateRequest          # DTO
│   └── CaseUpdateRequest          # DTO
├── encounter/
│   ├── Encounter                  # Entity
│   ├── EncounterStatus            # Enum
│   ├── EncounterRepository
│   ├── EncounterService
│   ├── EncounterController
│   ├── EncounterCreateRequest     # DTO
│   └── CaseTakingForm             # DTO (multi-section form)
├── clinical/
│   ├── Symptom / SymptomRepository
│   ├── Vitals / VitalsRepository
│   ├── ClinicalExamination / ClinicalExaminationRepository
│   ├── Diagnosis / DiagnosisRepository
│   ├── Treatment / TreatmentRepository
│   ├── FollowUp / FollowUpRepository
│   ├── Severity / Onset / DiagnosisStatus / FollowUpStatus   # Enums
├── appointment/
│   ├── Appointment                # Entity
│   ├── AppointmentStatus          # Enum
│   ├── AppointmentRepository
│   ├── AppointmentService
│   ├── AppointmentController
│   └── AppointmentCreateRequest   # DTO
├── document/
│   ├── Document                   # Entity
│   ├── DocumentRepository
│   ├── DocumentService
│   └── DocumentController
├── dashboard/
│   ├── DashboardService
│   └── DashboardController
├── audit/
│   ├── AuditLog                   # Entity
│   ├── AuditAction                # Enum
│   ├── AuditLogRepository
│   └── AuditService
└── common/
    └── ResourceNotFoundException
```

---

## Request Flow

### Typical GET Request

1. Browser requests `GET /patients`
2. Spring Security checks authentication and ROLE_* authorization
3. `PatientController.listPatients()` called
4. `PatientService.searchPatients()` queries database via `PatientRepository`
5. Results wrapped in Spring Data `Page<Patient>`
6. Model populated with `patients`, `search`, pagination state
7. Thymeleaf renders `patients/list.html` with fragment composition
8. HTML response returned to browser

### Typical POST Request (e.g., create patient)

1. Browser submits form `POST /patients/new`
2. CSRF token validated by Spring Security
3. Controller receives `@Valid PatientCreateRequest` DTO
4. Bean Validation runs; on error → return form with error messages
5. `PatientService.createPatient()` creates entity, generates patient number
6. `AuditService.log()` records `PATIENT_CREATED` action
7. Redirect to patient profile with flash success message

---

## Authentication

Spring Security form-based authentication:

- Login page: `GET /login` → `auth/login.html`
- Login processing: `POST /login` (handled by Spring Security)
- Success → redirect to `/dashboard`
- Failure → redirect to `/login?error=true`
- Logout: `POST /logout` (CSRF-protected) → redirect to `/login?logout=true`

Passwords hashed with BCrypt (cost 12) via `BCryptPasswordEncoder`.

`UserDetailsServiceImpl` loads `User` from database by username and wraps in Spring Security `UserDetails` with `ROLE_<role>` authority.

---

## Authorization

Role-based access control via Spring Security `authorizeHttpRequests`:

| Path Pattern | Required Role(s) |
|-------------|-----------------|
| `/admin/**` | ADMIN |
| `/patients/**` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `/cases/**` | ADMIN, DOCTOR, NURSE |
| `/encounters/**` | ADMIN, DOCTOR, NURSE |
| `/appointments/**` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `/documents/**` | ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| `/dashboard` | Any authenticated |
| `/login`, `/css/**`, `/js/**` | Public |

---

## Database

### Schema Management

Flyway handles all schema creation and migrations:

- `V1__initial_schema.sql` — All tables, constraints, indexes
- `V2__demo_data.sql` — Fictional demo users, patients, cases, encounters

`spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates schema against entities but does not modify it.

### Entity Relationships

```
User
 ├── Encounter (clinician_id)
 ├── Appointment (clinician_id)
 ├── Document (uploaded_by)
 └── AuditLog (user_id)

Patient
 ├── PatientCase (patient_id)
 ├── Appointment (patient_id)
 └── Document (patient_id)

PatientCase
 ├── Encounter (case_id)
 └── Document (case_id)

Encounter
 ├── Symptom (encounter_id)
 ├── Vitals (encounter_id)
 ├── ClinicalExamination (encounter_id)
 ├── Diagnosis (encounter_id)
 ├── Treatment (encounter_id)
 ├── FollowUp (encounter_id)
 └── Document (encounter_id)
```

---

## Validation

Two layers of validation:

1. **Client-side (HTML5):** `required`, `type="email"`, `min`, `max` attributes for immediate feedback
2. **Server-side (Bean Validation):** `@Valid` on DTO parameters, `@NotBlank`, `@Past`, `@Email`, `@Size`, `@DecimalMin`, `@DecimalMax`

Validation errors are returned to the form via `BindingResult` and rendered with Thymeleaf `th:errors`.

---

## Audit Logging

`AuditService` records key actions in the `audit_logs` table:

- Logged actions: LOGIN, LOGOUT, PATIENT_CREATED, PATIENT_UPDATED, CASE_CREATED, CASE_UPDATED, ENCOUNTER_CREATED, ENCOUNTER_UPDATED, DIAGNOSIS_CREATED, TREATMENT_CREATED, APPOINTMENT_CREATED, APPOINTMENT_UPDATED, DOCUMENT_UPLOADED
- Records: `user_id`, `username`, `action`, `entity_type`, `entity_id`, `metadata`, `ip_address`, `created_at`
- **Never logged:** passwords, password hashes, clinical details, request bodies

Audit logging uses `REQUIRES_NEW` transaction propagation to ensure audit records are saved even if the main transaction rolls back.

---

## Document Storage

Documents are stored on the local filesystem **outside the web root** to prevent direct access.

Storage path: configurable via `app.document.storage-path` (default: `~/patientcase-documents`).

Security measures:
- Allowed MIME types whitelist (PDF, images, Word docs)
- Maximum file size: 10MB
- UUID-based filenames (original filename sanitized and stored as metadata)
- Path traversal prevention via `Path.normalize().startsWith(storageDir)` check
- Executable file types rejected

For production, replace `DocumentService.uploadDocument()` with cloud storage integration.

---

## Separation of Clinician vs. AI Information

This system does **not** generate AI diagnoses or suggestions. All clinical information (chief complaint, history, symptoms, assessment, diagnosis, treatment) is **clinician-entered**.

The application name "AI Patient Case-Taking" refers to the domain (AI-assisted case-taking workflow), not to automated AI medical inference.

A disclaimer is shown in the sidebar, footer, login page, and case-taking interface.

---

## Testing Strategy

Tests use `@SpringBootTest` with H2 in-memory database (`application-test.yml`).

Test classes:
- `AuthControllerTest` — Authentication, login/logout, redirects
- `PatientServiceTest` — Patient CRUD, search, validation
- `PatientCaseServiceTest` — Case creation, retrieval, update
- `SecurityAccessTest` — Role-based access control, unauthorized access

Flyway is disabled in tests; H2 with `ddl-auto=create-drop` creates the schema from entities.
