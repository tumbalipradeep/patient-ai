-- ============================================================
-- V2: Demo Data
-- All data is entirely fictional for demonstration purposes.
-- BCrypt hashes for password "Admin@123" (admin), "Doctor@123" (doctor/nurse/receptionist)
-- ============================================================

-- Demo Users
-- Admin: username=admin, password=Admin@123
-- Doctor: username=dr.smith, password=Doctor@123
-- Nurse: username=nurse.jones, password=Doctor@123
-- Receptionist: username=reception, password=Doctor@123

INSERT INTO users (username, email, password_hash, first_name, last_name, role, enabled)
VALUES
('admin',
 'admin@patientcase.local',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCBGkWh5L.V3W.UqGmOH/uS',
 'System', 'Administrator', 'ADMIN', TRUE),
('dr.smith',
 'dr.smith@patientcase.local',
 '$2a$12$eVMIpMlE5Q1NkOzN98C5guM1O4WvZfCxGHVn.NJfD0sR7XwM9lO9i',
 'James', 'Smith', 'DOCTOR', TRUE),
('dr.johnson',
 'dr.johnson@patientcase.local',
 '$2a$12$eVMIpMlE5Q1NkOzN98C5guM1O4WvZfCxGHVn.NJfD0sR7XwM9lO9i',
 'Sarah', 'Johnson', 'DOCTOR', TRUE),
('nurse.jones',
 'nurse.jones@patientcase.local',
 '$2a$12$eVMIpMlE5Q1NkOzN98C5guM1O4WvZfCxGHVn.NJfD0sR7XwM9lO9i',
 'Emily', 'Jones', 'NURSE', TRUE),
('reception',
 'reception@patientcase.local',
 '$2a$12$eVMIpMlE5Q1NkOzN98C5guM1O4WvZfCxGHVn.NJfD0sR7XwM9lO9i',
 'Michael', 'Brown', 'RECEPTIONIST', TRUE);

-- Demo Patients (entirely fictional)
INSERT INTO patients (patient_number, first_name, last_name, date_of_birth, gender, phone, email, address,
                      emergency_contact_name, emergency_contact_phone, blood_group, allergies)
VALUES
('P-001001', 'Alice', 'Henderson', '1978-03-15', 'FEMALE',
 '555-0101', 'alice.henderson@example.com',
 '123 Maple Street, Springfield, ST 12345',
 'Robert Henderson', '555-0102', 'O+',
 'Penicillin'),
('P-001002', 'Brian', 'Caldwell', '1965-07-22', 'MALE',
 '555-0201', 'brian.caldwell@example.com',
 '456 Oak Avenue, Riverside, ST 12346',
 'Carol Caldwell', '555-0202', 'A+',
 'None known'),
('P-001003', 'Caroline', 'Nguyen', '1990-11-08', 'FEMALE',
 '555-0301', 'caroline.nguyen@example.com',
 '789 Pine Road, Lakeside, ST 12347',
 'David Nguyen', '555-0302', 'B-',
 'Sulfa drugs, Aspirin'),
('P-001004', 'Daniel', 'Ortega', '1955-01-30', 'MALE',
 '555-0401', 'daniel.ortega@example.com',
 '321 Elm Court, Hillview, ST 12348',
 'Maria Ortega', '555-0402', 'AB+',
 'None known'),
('P-001005', 'Elena', 'Petrova', '2001-05-19', 'FEMALE',
 '555-0501', 'elena.petrova@example.com',
 '654 Birch Lane, Westfield, ST 12349',
 'Igor Petrov', '555-0502', 'A-',
 'Latex');

-- Demo Cases
INSERT INTO patient_cases (case_number, patient_id, title, chief_complaint, status, priority)
VALUES
('C-2024-001', 1, 'Recurring Headaches and Fatigue',
 'Patient reports persistent headaches for 2 weeks, associated with fatigue and mild dizziness',
 'IN_PROGRESS', 'MEDIUM'),
('C-2024-002', 2, 'Chest Discomfort Assessment',
 'Patient presents with intermittent chest discomfort on exertion for 1 month',
 'OPEN', 'HIGH'),
('C-2024-003', 3, 'Skin Rash Evaluation',
 'Generalised skin rash noted for 5 days, mildly pruritic',
 'CLOSED', 'LOW'),
('C-2024-004', 4, 'Annual Health Review',
 'Routine annual health review and medication assessment',
 'OPEN', 'LOW'),
('C-2024-005', 1, 'Follow-up: Blood Pressure Management',
 'Follow-up for previously identified elevated blood pressure readings',
 'OPEN', 'MEDIUM');

-- Demo Encounters
INSERT INTO encounters (case_id, clinician_id, encounter_date, notes, chief_complaint,
                        history_of_present_illness, assessment_notes, status)
VALUES
(1, 2, CURRENT_TIMESTAMP - INTERVAL '5 days',
 'Initial assessment completed.',
 'Recurring headaches and fatigue',
 'Patient reports 2 weeks of frontal headaches, 7/10 severity, worse in evenings. Associated fatigue and occasional dizziness. No visual changes. Denies fever.',
 'Likely tension-type headache. Consider further evaluation if no improvement.',
 'COMPLETED'),
(1, 2, CURRENT_TIMESTAMP - INTERVAL '1 day',
 'Follow-up assessment.',
 'Headache follow-up',
 'Headaches persist despite rest. No improvement noted. Sleep quality poor.',
 'Persistent tension headache. Reviewing contributing factors.',
 'DRAFT'),
(3, 3, CURRENT_TIMESTAMP - INTERVAL '10 days',
 'Rash assessment completed.',
 'Generalised skin rash',
 'Rash appeared suddenly 5 days before presentation. Mildly itchy. No new medications. No travel history.',
 'Contact dermatitis pattern. Advised to avoid potential irritants.',
 'COMPLETED');

-- Demo Symptoms
INSERT INTO symptoms (encounter_id, name, duration, severity, onset, notes)
VALUES
(1, 'Headache', '2 weeks', 'MODERATE', 'GRADUAL', 'Frontal, worse in evenings'),
(1, 'Fatigue', '2 weeks', 'MILD', 'GRADUAL', 'General tiredness throughout day'),
(1, 'Dizziness', '1 week', 'MILD', 'GRADUAL', 'Occasional, no syncope'),
(3, 'Skin rash', '5 days', 'MILD', 'SUDDEN', 'Generalised, mildly pruritic');

-- Demo Vitals
INSERT INTO vitals (encounter_id, temperature, heart_rate, systolic_bp, diastolic_bp,
                    respiratory_rate, oxygen_saturation, height, weight)
VALUES
(1, 37.1, 78, 128, 82, 16, 98.5, 165.0, 68.0),
(3, 36.9, 72, 118, 76, 14, 99.0, 158.0, 62.0);

-- Demo Clinical Examinations
INSERT INTO clinical_examinations (encounter_id, examination_area, findings, notes)
VALUES
(1, 'Neurological', 'Alert and oriented. No focal neurological deficits. Cranial nerves intact.', 'GCS 15'),
(1, 'Cardiovascular', 'Regular rate and rhythm. No murmurs.', NULL),
(3, 'Dermatology', 'Erythematous papular rash over trunk and upper extremities. No vesicles. No signs of secondary infection.', 'Bilateral, symmetrical distribution');

-- Demo Diagnoses
INSERT INTO diagnoses (encounter_id, diagnosis, notes, status, created_by)
VALUES
(1, 'Tension-type headache', 'Likely stress-related. Monitor for progression.', 'SUSPECTED', 2),
(3, 'Contact dermatitis', 'Advise avoidance of identified irritants.', 'CONFIRMED', 2);

-- Demo Treatments
INSERT INTO treatments (encounter_id, treatment, instructions, notes, created_by)
VALUES
(1, 'Rest and hydration', 'Ensure adequate rest and fluid intake. Avoid screen time before bed.', NULL, 2),
(1, 'Analgesic as needed', 'Use as directed by clinician. Do not exceed recommended dose.', 'Review if symptoms worsen', 2),
(3, 'Topical corticosteroid cream', 'Apply thin layer to affected areas twice daily for 7 days.', 'Avoid face unless directed', 2);

-- Demo Follow-ups
INSERT INTO follow_ups (encounter_id, case_id, follow_up_date, instructions, notes, status)
VALUES
(1, 1, CURRENT_DATE + INTERVAL '7 days', 'Return if headaches worsen or new symptoms develop.', NULL, 'PENDING'),
(3, 3, CURRENT_DATE - INTERVAL '3 days', 'Review at 1 week. Assess rash resolution.', 'Resolved as expected.', 'COMPLETED');

-- Demo Appointments
INSERT INTO appointments (patient_id, clinician_id, appointment_datetime, status, reason, notes)
VALUES
(1, 2, CURRENT_TIMESTAMP + INTERVAL '1 day', 'CONFIRMED', 'Headache follow-up', NULL),
(2, 2, CURRENT_TIMESTAMP + INTERVAL '2 days', 'SCHEDULED', 'Chest discomfort assessment', 'Patient requested morning slot'),
(4, 3, CURRENT_TIMESTAMP + INTERVAL '3 days', 'SCHEDULED', 'Annual health review', NULL),
(5, 2, CURRENT_TIMESTAMP + INTERVAL '5 days', 'SCHEDULED', 'Initial consultation', NULL),
(3, 3, CURRENT_TIMESTAMP - INTERVAL '3 days', 'COMPLETED', 'Rash follow-up', 'Resolved');

-- Demo Audit Logs
INSERT INTO audit_logs (user_id, username, action, entity_type, entity_id, metadata)
VALUES
(1, 'admin', 'LOGIN', NULL, NULL, 'Demo data initialization'),
(2, 'dr.smith', 'PATIENT_CREATED', 'Patient', 1, 'Patient P-001001 created'),
(2, 'dr.smith', 'CASE_CREATED', 'PatientCase', 1, 'Case C-2024-001 created'),
(2, 'dr.smith', 'ENCOUNTER_CREATED', 'Encounter', 1, 'Encounter for case C-2024-001');
