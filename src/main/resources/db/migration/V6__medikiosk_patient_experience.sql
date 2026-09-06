-- ============================================================
-- V6: MediKiosk Patient Experience
-- Adds patient self-service architecture:
--   - link patients to their login account (user_id)
--   - PATIENT role in the users role check
--   - consents (explicit, auditable patient consent)
--   - kiosk_intakes (patient-driven intake submissions)
--   - red_flags (patient-safety observations for triage)
--   - ayush_assessments (Dashavidha Pariksha structured capture)
--   - document_extractions (OCR/digitization metadata)
-- ============================================================

-- Extend allowed roles with PATIENT (self-service kiosk accounts)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'PATIENT'));

-- Link a Patient record to its self-service login account.
-- Nullable: staff-created patients may not have a patient portal login.
ALTER TABLE patients ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE UNIQUE INDEX uq_patients_user_id ON patients(user_id);
CREATE INDEX idx_patients_email ON patients(email);

-- ============================================================
-- Consents — explicit, purpose-oriented, auditable consent records
-- ============================================================
CREATE TABLE consents (
    id            BIGSERIAL PRIMARY KEY,
    patient_id    BIGINT NOT NULL REFERENCES patients(id),
    user_id       BIGINT REFERENCES users(id),
    purpose       VARCHAR(150) NOT NULL,
    version       VARCHAR(50),
    status        VARCHAR(20) NOT NULL DEFAULT 'GRANTED'
                  CHECK (status IN ('GRANTED', 'REVOKED')),
    ip_address    VARCHAR(50),
    granted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at    TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consents_patient_id ON consents(patient_id);
CREATE INDEX idx_consents_status ON consents(status);

-- ============================================================
-- Kiosk Intakes — patient self-service intake submissions
-- Messages/draft follow the same security model as ai_intake_sessions
-- (validated draft, no raw AI JSON logged, reviewed by clinician).
-- ============================================================
CREATE TABLE kiosk_intakes (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT NOT NULL REFERENCES patients(id),
    user_id          BIGINT REFERENCES users(id),
    language         VARCHAR(10) NOT NULL DEFAULT 'en',
    status           VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'
                     CHECK (status IN ('IN_PROGRESS', 'DRAFT_READY', 'SUBMITTED',
                                       'ACCEPTED', 'REJECTED')),
    consent_id       BIGINT REFERENCES consents(id),
    messages_json    TEXT,
    draft_json       TEXT,
    red_flags_json   TEXT,
    ayush_json       TEXT,
    summary_json     TEXT,
    reviewed_by      VARCHAR(100),
    reviewed_at      TIMESTAMP,
    clinician_notes  TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kiosk_intakes_patient_id ON kiosk_intakes(patient_id);
CREATE INDEX idx_kiosk_intakes_status ON kiosk_intakes(status);
CREATE INDEX idx_kiosk_intakes_created_at ON kiosk_intakes(created_at);

-- ============================================================
-- Red Flags — patient-safety observations for triage awareness.
-- NOT diagnoses. Identified from patient-reported information or
-- flagged by a clinician during review.
-- ============================================================
CREATE TABLE red_flags (
    id             BIGSERIAL PRIMARY KEY,
    intake_id      BIGINT REFERENCES kiosk_intakes(id) ON DELETE CASCADE,
    encounter_id   BIGINT REFERENCES encounters(id) ON DELETE CASCADE,
    patient_id     BIGINT REFERENCES patients(id),
    description    VARCHAR(500) NOT NULL,
    urgent         BOOLEAN NOT NULL DEFAULT FALSE,
    source         VARCHAR(30) NOT NULL DEFAULT 'AI_INTELLIGENCE'
                   CHECK (source IN ('AI_INTELLIGENCE', 'CLINICIAN')),
    resolved       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_red_flags_intake_id ON red_flags(intake_id);
CREATE INDEX idx_red_flags_encounter_id ON red_flags(encounter_id);
CREATE INDEX idx_red_flags_patient_id ON red_flags(patient_id);
CREATE INDEX idx_red_flags_resolved ON red_flags(resolved);

-- ============================================================
-- AYUSH Assessments — Dashavidha Pariksha structured capture plus
-- Ahara-Vihara details. Integrated into the intake/encounter workflow.
-- ============================================================
CREATE TABLE ayush_assessments (
    id                BIGSERIAL PRIMARY KEY,
    intake_id         BIGINT REFERENCES kiosk_intakes(id) ON DELETE CASCADE,
    encounter_id      BIGINT REFERENCES encounters(id) ON DELETE CASCADE,
    prakriti          VARCHAR(100),
    vikriti           VARCHAR(100),
    sara              VARCHAR(100),
    samhanana         VARCHAR(100),
    pramana           VARCHAR(100),
    satmya            VARCHAR(100),
    satva             VARCHAR(100),
    ahara_shakti      VARCHAR(100),
    vyayama_shakti    VARCHAR(100),
    vaya              VARCHAR(100),
    ahara_details     TEXT,
    vihara_details    TEXT,
    notes             TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ayush_intake_id ON ayush_assessments(intake_id);
CREATE INDEX idx_ayush_encounter_id ON ayush_assessments(encounter_id);

-- ============================================================
-- Document Extractions — digitization metadata for uploaded
-- medical documents (OCR/AI processing results).
-- ============================================================
CREATE TABLE document_extractions (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    intake_id       BIGINT REFERENCES kiosk_intakes(id) ON DELETE CASCADE,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED',
                                      'FAILED', 'UNSUPPORTED')),
    provider        VARCHAR(50),
    extracted_json  TEXT,
    error_message   VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_extraction_document UNIQUE (document_id)
);

CREATE INDEX idx_document_extractions_intake_id ON document_extractions(intake_id);
CREATE INDEX idx_document_extractions_status ON document_extractions(status);