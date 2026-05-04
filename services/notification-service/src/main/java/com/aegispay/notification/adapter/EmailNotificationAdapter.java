package com.aegispay.notification.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationAdapter {

    private final JavaMailSender mailSender;

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(String recipient, String title, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setSubject(title);
            message.setText(body);
            message.setFrom("noreply@aegispay.io");
            mailSender.send(message);
            log.debug("Email sent to {}", recipient);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }
}
