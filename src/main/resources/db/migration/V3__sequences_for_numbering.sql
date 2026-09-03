-- ============================================================
-- V3: Add sequences for concurrency-safe patient/case numbering
-- ============================================================

CREATE SEQUENCE patient_number_seq
    START WITH 1001
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE case_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

-- Advance sequences past any existing records to avoid collisions
SELECT setval('patient_number_seq', GREATEST(1001, (SELECT COUNT(*) + 1001 FROM patients)));
SELECT setval('case_number_seq',    GREATEST(1,    (SELECT COUNT(*) + 1    FROM patient_cases)));
