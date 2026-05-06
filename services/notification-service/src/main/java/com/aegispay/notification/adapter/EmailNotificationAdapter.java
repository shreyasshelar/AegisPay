package com.aegispay.notification.adapter;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends HTML email notifications via JavaMailSender (configured for Gmail SMTP).
 *
 * <p>Uses MimeMessage (HTML) rather than SimpleMailMessage so we can send
 * styled notification emails.  The {@code from} address is read from
 * {@code SMTP_FROM_ADDRESS} env var so it matches the authenticated sender
 * (Gmail rejects mismatched from addresses).
 */
@Slf4j
@Component
public class EmailNotificationAdapter implements NotificationAdapter {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${spring.mail.from:aegispay.dev@gmail.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(String recipient, String title, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(title);
            // Send as HTML — wrap plain text in minimal HTML for nicer rendering
            helper.setText(toHtml(body), true);
            mailSender.send(message);
            log.debug("Email sent to {}", recipient);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }

    /** Wraps a plain-text body in minimal styled HTML. */
    private String toHtml(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
        return """
                <!DOCTYPE html>
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px">
                  <div style="border-left:4px solid #0070f3;padding-left:16px">
                    <p>%s</p>
                  </div>
                  <hr style="margin-top:32px;border:none;border-top:1px solid #eee"/>
                  <p style="font-size:12px;color:#999">AegisPay — Secure Digital Payments</p>
                </body></html>
                """.formatted(escaped);
    }
}
