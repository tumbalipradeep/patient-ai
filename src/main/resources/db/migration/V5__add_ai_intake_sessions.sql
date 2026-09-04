-- ============================================================
-- V5: AI Intake Sessions
-- Persists conversation history and validated draft for the
-- AI-assisted patient intake workflow.
-- ============================================================

CREATE TABLE ai_intake_sessions (
    id             BIGSERIAL PRIMARY KEY,
    encounter_id   BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    messages_json  TEXT,
    draft_json     TEXT,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_intake_session_encounter UNIQUE (encounter_id)
);

CREATE INDEX idx_ai_intake_sessions_encounter ON ai_intake_sessions(encounter_id);
CREATE INDEX idx_ai_intake_sessions_status    ON ai_intake_sessions(status);
