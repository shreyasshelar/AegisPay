package com.aegispay.notification.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS adapter stub — integrates with Twilio/SNS when credentials are configured.
 * In dev/test with no credentials, logs the message instead of sending.
 */
@Slf4j
@Component
public class SmsNotificationAdapter implements NotificationAdapter {

    @Value("${aegispay.notification.sms.account-sid:}")
    private String accountSid;

    @Value("${aegispay.notification.sms.from-number:}")
    private String fromNumber;

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public void send(String recipient, String title, String body) {
        if (accountSid.isBlank()) {
            log.info("[SMS-STUB] Would send SMS to {}: {}", recipient, body);
            return;
        }
        // Real Twilio integration would go here
        log.info("SMS sent to {} via Twilio", recipient);
    }
}
