-- V10: Consolidate all seeded user phone numbers to the project owner's test number.
--
-- All four seeded customers (customer, payee, alice, bob) share one real number so
-- that SMS alerts land on a single device during development and demo walkthroughs.
--
-- V5 / V8 are updated for fresh DB setups, but those migrations use
-- ON CONFLICT DO NOTHING so they do not update existing rows.  This migration
-- handles existing dev databases that were seeded before V10.
--
-- The CTE pattern ensures outbox entries are only inserted for rows that actually
-- changed — on a fresh DB (V5/V8 already seeded the correct number) the UPDATE
-- matches zero rows, the CTE returns nothing, and no outbox entries are inserted.
-- No duplicates, fully idempotent.
WITH updated AS (
    UPDATE users
    SET    phone = '+918432204040'
    WHERE  external_id IN (
               '56903685-bde3-45a4-80b9-d0d40432d548',   -- customer
               '4dfa538c-f408-4e2a-91b4-eba86846f39e',   -- payee
               'c1a2b3c4-d5e6-4f78-9012-abcdef123456',   -- alice
               'd2b3c4d5-e6f7-4089-0123-bcdef1234567'    -- bob
           )
      AND  phone IS DISTINCT FROM '+918432204040'
    RETURNING id, phone
)
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
FROM updated u;
