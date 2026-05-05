package com.aegispay.notification.template;

import com.aegispay.common.domain.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationTemplateService {

    public record RenderedNotification(String title, String body) {}

    public RenderedNotification render(NotificationType type, Map<String, String> vars, String locale) {
        return switch (type) {
            case TRANSACTION_COMPLETED -> new RenderedNotification(
                    "Payment Successful",
                    "Your payment of " + vars.getOrDefault("amount", "?") + " " +
                    vars.getOrDefault("currency", "") + " has been processed successfully. " +
                    "Reference: " + vars.getOrDefault("externalReference", "N/A"));

            case TRANSACTION_FAILED -> new RenderedNotification(
                    "Payment Failed",
                    "Your payment could not be processed. Reason: " +
                    vars.getOrDefault("failureReason", "Unknown"));

            case TRANSACTION_ROLLED_BACK -> new RenderedNotification(
                    "Payment Reversed",
                    "Your payment has been reversed. " +
                    vars.getOrDefault("rollbackReason", ""));

            case KYC_STATUS_CHANGED -> new RenderedNotification(
                    "KYC Status Update",
                    "Your KYC status has been updated to: " +
                    vars.getOrDefault("newStatus", "Unknown"));

            case USER_REGISTERED -> new RenderedNotification(
                    "Welcome to AegisPay!",
                    "Welcome! Your account has been created successfully. " +
                    "Please complete your KYC to start transacting.");

            case GENERIC -> new RenderedNotification(
                    vars.getOrDefault("title", "Notification"),
                    vars.getOrDefault("body", ""));
        };
    }
}
