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
 * Slack adapter — posts to an Incoming Webhook URL.
 *
 * <p>Configure via {@code SLACK_WEBHOOK_URL} environment variable.
 * When blank, the adapter logs the message and returns without sending (stub mode).
 *
 * <p>Slack webhook setup:
 * 1. Go to https://api.slack.com/apps → Your App → Incoming Webhooks
 * 2. Activate and add webhook to a channel
 * 3. Copy the webhook URL into the env var / K8s secret
 *
 * <p>Message format: standard Slack Blocks (single section with markdown).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationAdapter implements NotificationAdapter {

    private static final String SLACK_API_URL = "https://slack.com/api/chat.postMessage";

    @Value("${aegispay.notification.slack.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String channel() {
        return "SLACK";
    }

    @Override
    public void send(String recipient, String title, String body) {
        // recipient may be a specific webhook URL override; fall back to configured default
        String url = (recipient != null && recipient.startsWith("https://hooks.slack.com/")) ? recipient : webhookUrl;
        if (url.isBlank()) {
            log.info("[SLACK-STUB] SLACK_WEBHOOK_URL not set — would post: [{}] {}", title, body);
            return;
        }

        try {
            // Slack Incoming Webhook payload — blocks for rich formatting
            String payload = objectMapper.writeValueAsString(Map.of(
                "blocks", new Object[]{
                    Map.of(
                        "type", "section",
                        "text", Map.of(
                            "type", "mrkdwn",
                            "text", "*" + escapeMarkdown(title) + "*\n" + escapeMarkdown(body)
                        )
                    )
                }
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload, headers),
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Slack notification sent: title={}", title);
            } else {
                log.warn("Slack webhook non-2xx: status={} body={}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception ex) {
            // Slack failure must never crash the notification pipeline
            log.error("Slack send failed: {}", ex.getMessage());
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
