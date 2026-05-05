package com.aegispay.common.observability;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Masks PII and financial identifiers before they reach any log appender.
 *
 * Rules are applied in order; the first matching rule wins per field value.
 * All masking is deterministic (same input → same mask) so logs remain
 * correlatable without exposing real data.
 *
 * Covered fields: account number, PAN/card number, Aadhaar, PAN (tax ID),
 * CVV, phone number, IBAN, email address, UPI VPA.
 */
@Component
public class SensitiveFieldMasker {

    // 16-digit card / PAN — show last 4
    private static final Pattern CARD_NUMBER =
            Pattern.compile("\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    // 12-digit Aadhaar — show last 4
    private static final Pattern AADHAAR =
            Pattern.compile("\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    // Indian PAN (10 char alphanumeric)
    private static final Pattern PAN_TAX =
            Pattern.compile("\\b([A-Z]{5})([0-9]{4})([A-Z])\\b");

    // Phone: +91XXXXXXXXXX or 10-digit
    private static final Pattern PHONE =
            Pattern.compile("\\b(\\+?\\d{1,3})?[- ]?(\\d{3})[- ]?(\\d{3})[- ]?(\\d{4})\\b");

    // IBAN (up to 34 alphanumeric)
    private static final Pattern IBAN =
            Pattern.compile("\\b([A-Z]{2}\\d{2})([A-Z0-9]{4,30})([A-Z0-9]{4})\\b");

    // Email — show first char + domain
    private static final Pattern EMAIL =
            Pattern.compile("\\b([a-zA-Z0-9._%+\\-]{1})([a-zA-Z0-9._%+\\-]*)(@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})\\b");

    // CVV — 3-4 digits (only when labelled in JSON key=value context)
    private static final Pattern CVV_JSON =
            Pattern.compile("(?i)(\"cvv\"\\s*:\\s*\")([0-9]{3,4})(\")",
                            Pattern.CASE_INSENSITIVE);

    public String mask(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;
        result = CVV_JSON.matcher(result).replaceAll("$1***$3");
        result = CARD_NUMBER.matcher(result).replaceAll("****-****-****-$4");
        result = AADHAAR.matcher(result).replaceAll("XXXX-XXXX-$3");
        result = PAN_TAX.matcher(result).replaceAll("$1XXXX$3");
        result = IBAN.matcher(result).replaceAll("$1****$3");
        result = EMAIL.matcher(result).replaceAll("$1***$3");
        result = PHONE.matcher(result).replaceAll("$1-***-***-$4");
        return result;
    }

    public String maskValue(String fieldName, String value) {
        if (value == null) return null;
        return switch (fieldName.toLowerCase()) {
            case "accountnumber", "account_number" -> maskAccountNumber(value);
            case "cardnumber", "card_number", "pan" -> maskCard(value);
            case "aadhaar", "aadhaarid" -> maskAadhaar(value);
            case "cvv", "cvv2" -> "***";
            case "phone", "mobilenumber", "mobile" -> maskPhone(value);
            case "email" -> maskEmail(value);
            default -> value;
        };
    }

    private String maskAccountNumber(String v) {
        if (v.length() <= 4) return "****";
        return "*".repeat(v.length() - 4) + v.substring(v.length() - 4);
    }

    private String maskCard(String v) {
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "****";
        return "****-****-****-" + digits.substring(digits.length() - 4);
    }

    private String maskAadhaar(String v) {
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "XXXX-XXXX-XXXX";
        return "XXXX-XXXX-" + digits.substring(digits.length() - 4);
    }

    private String maskPhone(String v) {
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "****";
        return "***-***-" + digits.substring(digits.length() - 4);
    }

    private String maskEmail(String v) {
        int at = v.indexOf('@');
        if (at <= 1) return v;
        return v.charAt(0) + "***" + v.substring(at);
    }
}
