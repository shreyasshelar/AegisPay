package com.aegispay.notification.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * SMS adapter — sends via Fast2SMS Quick route (no DLT required for personal/dev use).
 * Falls back to log-only stub when FAST2SMS_API_KEY is not set.
 *
 * Fast2SMS Quick route limits: 200 SMS/day on free tier, ₹0.18/SMS on paid.
 * Sign up: https://www.fast2sms.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationAdapter implements NotificationAdapter {

    private static final String FAST2SMS_URL = "https://www.fast2sms.com/dev/bulkV2";

    @Value("${aegispay.notification.sms.fast2sms.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public void send(String recipient, String title, String body) {
        if (apiKey.isBlank()) {
            log.info("[SMS-STUB] FAST2SMS_API_KEY not set — would send to {}: {}", recipient, body);
            return;
        }

        // Fast2SMS bulkV2 expects a 10-digit Indian mobile number (no country code).
        // Strip leading +91 / 91 / 0 so "+919876543210" → "9876543210".
        String normalizedNumber = normalizeIndianPhone(recipient);
        if (normalizedNumber == null || normalizedNumber.length() != 10) {
            log.warn("Fast2SMS: skipping SMS — recipient '{}' is not a valid 10-digit Indian number", recipient);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Fast2SMS Quick route — no sender ID or DLT template needed
            String payload = objectMapper.writeValueAsString(Map.of(
                "route",   "q",
                "message", body,
                "numbers", normalizedNumber,
                "flash",   0
            ));

            ResponseEntity<String> response = restTemplate.postForEntity(
                FAST2SMS_URL,
                new HttpEntity<>(payload, headers),
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("SMS sent to {} (→{}) via Fast2SMS", recipient, normalizedNumber);
            } else {
                log.warn("Fast2SMS non-2xx: status={} body={}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception ex) {
            // SMS failure must never crash the notification pipeline
            log.error("Fast2SMS send failed for {}: {}", recipient, ex.getMessage());
        }
    }

    /**
     * Normalises any Indian phone number to the 10-digit format Fast2SMS requires.
     * Handles: "+919876543210", "919876543210", "09876543210", "9876543210".
     * Returns {@code null} for numbers that can't be resolved to 10 digits.
     */
    private static String normalizeIndianPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2); // 919876543210
        if (digits.length() == 11 && digits.startsWith("0"))  return digits.substring(1); // 09876543210
        if (digits.length() == 10)                             return digits;              // 9876543210
        return null; // unknown format — caller logs and skips
    }
}
