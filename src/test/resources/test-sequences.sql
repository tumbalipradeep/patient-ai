-- Create sequences needed for service tests (Flyway is disabled in test profile)
CREATE SEQUENCE IF NOT EXISTS patient_number_seq START WITH 1001 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS case_number_seq START WITH 1 INCREMENT BY 1;
