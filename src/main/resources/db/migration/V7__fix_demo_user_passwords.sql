-- ============================================================
-- V7: Fix demo user passwords
--
-- V2 shipped BCrypt hashes that do not match the documented demo
-- passwords ("Admin@123" / "Doctor@123"), so the documented staff
-- accounts could not be used to sign in on a fresh database.
--
-- This migration resets ONLY the users whose hashes equal the known
-- broken V2 hash values, so any password an administrator has since
-- set deliberately is left untouched.
--
-- Documented demo credentials after this migration:
--   admin       / Admin@123
--   dr.smith    / Doctor@123
--   dr.johnson  / Doctor@123
--   nurse.jones / Doctor@123
--   reception   / Doctor@123
-- ============================================================

UPDATE users
SET password_hash = '$2a$12$HenXorfHUw6Fws.lpTOJP.QYmOobbL.ztnWpninuCGEp./E6Q/6oW'
WHERE username = 'admin'
  AND password_hash = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCBGkWh5L.V3W.UqGmOH/uS';

UPDATE users
SET password_hash = '$2a$12$gRj1/hppuN.74u4pHs.8cu16XjlsAIm5fVA3DWjj7PZfjHDTzAudO'
WHERE username IN ('dr.smith', 'dr.johnson', 'nurse.jones', 'reception')
  AND password_hash = '$2a$12$eVMIpMlE5Q1NkOzN98C5guM1O4WvZfCxGHVn.NJfD0sR7XwM9lO9i';