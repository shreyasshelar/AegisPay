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

            case KYC_STATUS_CHANGED -> {
                String newStatus = vars.getOrDefault("newStatus", "");
                yield switch (newStatus) {
                    case "DOCUMENT_SUBMITTED" -> new RenderedNotification(
                            "Document Received",
                            "Your document has been received and is now being analysed by our AI system. " +
                            "We will notify you once the review is complete.");
                    case "AI_PROCESSING" -> new RenderedNotification(
                            "AI Processing",
                            "Your KYC document is being processed by AI. This usually takes a few minutes.");
                    case "APPROVED" -> new RenderedNotification(
                            "KYC Approved",
                            "Your identity has been verified. Your account now has full access to all features.");
                    case "REJECTED" -> new RenderedNotification(
                            "KYC Rejected",
                            "Your document could not be verified. Please re-upload a valid identity document.");
                    case "MANUAL_REVIEW" -> new RenderedNotification(
                            "KYC Under Review",
                            "Your document has been flagged for manual review. Our compliance team will contact you shortly.");
                    default -> new RenderedNotification(
                            "KYC Status Update",
                            "Your KYC status has been updated to: " + newStatus);
                };
            }

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
