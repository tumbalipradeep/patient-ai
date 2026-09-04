-- ============================================================
-- V4: Add must_change_password flag to users table
-- Used after admin password reset to force the user to choose
-- a new password on their next login.
-- ============================================================

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
