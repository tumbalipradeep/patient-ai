-- ============================================================
-- V8: Kiosk AI conversation idempotency + reset support
--
-- The patient-facing AI conversation ("MediKiosk Clinical History
-- Assistant") is driven by the server-side conversation history and
-- must tolerate client retries (timeout, duplicate Send) without
-- double-recording the same turn.
--
--   last_client_turn_id       The clientTurnId of the most recently
--                             processed conversation turn. A repeated
--                             POST with the same id returns the already
--                             stored reply instead of calling the AI
--                             provider again.
--
--   last_assistant_reply      The stored assistant reply for that turn,
--                             so a duplicate request can be answered
--                             from the server's own record.
--
-- These columns are nullable and only used by the online AI path;
-- guided intakes and existing rows are unaffected.
-- ============================================================

ALTER TABLE kiosk_intakes
    ADD COLUMN last_client_turn_id VARCHAR(64);

ALTER TABLE kiosk_intakes
    ADD COLUMN last_assistant_reply TEXT;