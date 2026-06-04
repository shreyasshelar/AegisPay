-- V9: Add SMS notification preference flag + bootstrap contact read-models for seeded users.
--
-- ── Step 1: Add the column ────────────────────────────────────────────────────
-- New rows default to FALSE — nobody receives SMS until they actively verify a
-- phone number via OTP.  UserService.updatePhone() auto-enables on verification.
ALTER TABLE users
    ADD COLUMN sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- ── Step 2: Auto-enable for existing users who already have a phone ───────────
-- Seeded dev/test users (customer, payee, alice, bob from V5/V8) were created via
-- SQL migration with pre-set phone numbers — they never went through the OTP flow.
-- Without this UPDATE they would have sms_notifications_enabled = FALSE and the
-- SMS toggle in the UI would show as OFF even though a verified number is on file.
--
-- Admin and back-office users have phone = NULL so they are unaffected (no SMS).
UPDATE users
SET    sms_notifications_enabled = TRUE
WHERE  phone IS NOT NULL;

-- ── Step 3: Bootstrap UserContactDocuments in notification-service MongoDB ────
-- Seeded users were created via SQL, not through POST /api/v1/users/register.
-- This means:
--   (a) No UserRegisteredEvent was ever published for them.
--   (b) No UserContactDocument exists in notification-service's MongoDB.
-- Without a MongoDB contact document, TransactionStatusConsumer.resolveContact()
-- returns Optional.empty() → SMS is never delivered regardless of phone status.
--
-- Fix: insert PENDING outbox entries that look exactly like UserContactUpdatedEvents.
-- The OutboxScheduler picks these up on next service startup and publishes them to
-- the 'user.contact.updated' Kafka topic.  UserContactUpdatedConsumer in the
-- notification-service creates/upserts the MongoDB UserContactDocument with
-- phoneNumber + smsNotificationsEnabled = true.
--
-- Uses INSERT...SELECT so it covers ALL users with phones automatically — not just
-- the hard-coded UUIDs from V5/V8 — and stays correct if future seed migrations
-- add more seeded users.  Flyway runs each migration exactly once so no
-- ON CONFLICT guard is needed on the outbox insert.
INSERT INTO outbox_entries (
    id,
    aggregate_id,
    aggregate_type,
    event_type,
    topic,
    message_key,
    payload,
    status,
    attempt_count,
    created_at
)
SELECT
    gen_random_uuid(),
    u.id::text,
    'User',
    'UserContactUpdatedEvent',
    'user.contact.updated',
    u.id::text,
    jsonb_build_object(
        'eventId',                 gen_random_uuid(),
        'correlationId',           NULL::uuid,
        'occurredAt',              now(),
        'schemaVersion',           1,
        'userId',                  u.id,
        'phoneNumber',             u.phone,
        'smsNotificationsEnabled', TRUE
    ),
    'PENDING',
    0,
    now()
FROM users u
WHERE u.phone IS NOT NULL;
