-- ============================================================
-- V1: Initial Schema
-- AI Patient Case-Taking System
-- ============================================================

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Patients table
CREATE TABLE patients (
    id BIGSERIAL PRIMARY KEY,
    patient_number VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    blood_group VARCHAR(5) CHECK (blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    allergies TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_patients_patient_number ON patients(patient_number);
CREATE INDEX idx_patients_last_name ON patients(last_name);
CREATE INDEX idx_patients_first_name ON patients(first_name);
CREATE INDEX idx_patients_phone ON patients(phone);

-- Patient Cases table
CREATE TABLE patient_cases (
    id BIGSERIAL PRIMARY KEY,
    case_number VARCHAR(20) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    title VARCHAR(255) NOT NULL,
    chief_complaint TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED', 'ARCHIVED')),
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cases_case_number ON patient_cases(case_number);
CREATE INDEX idx_cases_patient_id ON patient_cases(patient_id);
CREATE INDEX idx_cases_status ON patient_cases(status);
CREATE INDEX idx_cases_priority ON patient_cases(priority);

-- Encounters table
CREATE TABLE encounters (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES patient_cases(id),
    clinician_id BIGINT NOT NULL REFERENCES users(id),
    encounter_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    chief_complaint TEXT,
    history_of_present_illness TEXT,
    relevant_history TEXT,
    assessment_notes TEXT,
    clinical_impression TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_encounters_case_id ON encounters(case_id);
CREATE INDEX idx_encounters_clinician_id ON encounters(clinician_id);
CREATE INDEX idx_encounters_date ON encounters(encounter_date);
CREATE INDEX idx_encounters_status ON encounters(status);

-- Symptoms table
CREATE TABLE symptoms (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    duration VARCHAR(100),
    severity VARCHAR(10) NOT NULL DEFAULT 'MILD' CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE')),
    onset VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN' CHECK (onset IN ('SUDDEN', 'GRADUAL', 'UNKNOWN')),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_symptoms_encounter_id ON symptoms(encounter_id);

-- Vitals table
CREATE TABLE vitals (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    temperature NUMERIC(4,1),
    heart_rate INTEGER,
    systolic_bp INTEGER,
    diastolic_bp INTEGER,
    respiratory_rate INTEGER,
    oxygen_saturation NUMERIC(4,1),
    height NUMERIC(5,1),
    weight NUMERIC(5,1),
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

CREATE INDEX idx_vitals_encounter_id ON vitals(encounter_id);

-- Clinical Examinations table
CREATE TABLE clinical_examinations (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    examination_area VARCHAR(255) NOT NULL,
    findings TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_examinations_encounter_id ON clinical_examinations(encounter_id);

-- Diagnoses table
CREATE TABLE diagnoses (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    diagnosis VARCHAR(500) NOT NULL,
    notes TEXT,
    status VARCHAR(15) NOT NULL DEFAULT 'SUSPECTED' CHECK (status IN ('SUSPECTED', 'CONFIRMED', 'RULED_OUT')),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diagnoses_encounter_id ON diagnoses(encounter_id);

-- Treatments table
CREATE TABLE treatments (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    treatment VARCHAR(500) NOT NULL,
    instructions TEXT,
    notes TEXT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_treatments_encounter_id ON treatments(encounter_id);

-- Follow-ups table
CREATE TABLE follow_ups (
    id BIGSERIAL PRIMARY KEY,
    encounter_id BIGINT NOT NULL REFERENCES encounters(id) ON DELETE CASCADE,
    case_id BIGINT REFERENCES patient_cases(id),
    follow_up_date DATE,
    instructions TEXT,
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'OVERDUE', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_follow_ups_encounter_id ON follow_ups(encounter_id);
CREATE INDEX idx_follow_ups_follow_up_date ON follow_ups(follow_up_date);

-- Appointments table
CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    clinician_id BIGINT NOT NULL REFERENCES users(id),
    appointment_datetime TIMESTAMP NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    reason VARCHAR(500),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX idx_appointments_clinician_id ON appointments(clinician_id);
CREATE INDEX idx_appointments_datetime ON appointments(appointment_datetime);
CREATE INDEX idx_appointments_status ON appointments(status);

-- Documents table
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_reference VARCHAR(500) NOT NULL,
    patient_id BIGINT REFERENCES patients(id),
    case_id BIGINT REFERENCES patient_cases(id),
    encounter_id BIGINT REFERENCES encounters(id),
    uploaded_by BIGINT NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT
);

CREATE INDEX idx_documents_patient_id ON documents(patient_id);
CREATE INDEX idx_documents_case_id ON documents(case_id);
CREATE INDEX idx_documents_encounter_id ON documents(encounter_id);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);

-- Audit Log table
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    username VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    metadata TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
