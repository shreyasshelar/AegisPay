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

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Fast2SMS Quick route — no sender ID or DLT template needed
            String payload = objectMapper.writeValueAsString(Map.of(
                "route",   "q",
                "message", body,
                "numbers", recipient,
                "flash",   0
            ));

            ResponseEntity<String> response = restTemplate.postForEntity(
                FAST2SMS_URL,
                new HttpEntity<>(payload, headers),
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("SMS sent to {} via Fast2SMS", recipient);
            } else {
                log.warn("Fast2SMS non-2xx: status={} body={}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception ex) {
            // SMS failure must never crash the notification pipeline
            log.error("Fast2SMS send failed for {}: {}", recipient, ex.getMessage());
        }
    }
}
