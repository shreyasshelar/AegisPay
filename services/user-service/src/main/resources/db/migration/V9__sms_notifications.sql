-- V9: Add SMS notification preference flag to users table.
--
-- Default FALSE: nobody receives SMS until they actively verify a phone number.
-- auto-enabled to TRUE by the application when a verified phone number is first
-- saved (UserService.updatePhone). Users can then toggle it OFF/ON independently
-- via PATCH /api/v1/users/{userId}/notifications/sms.
--
-- Gate in notification-service: SMS is only dispatched when BOTH
--   users.sms_notifications_enabled = TRUE  AND  phone IS NOT NULL.
ALTER TABLE users
    ADD COLUMN sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;
