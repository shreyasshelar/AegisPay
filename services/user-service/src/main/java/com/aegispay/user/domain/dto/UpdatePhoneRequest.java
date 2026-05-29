package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
    /**
     * Phone number in international format (e.g. {@code +919876543210}).
     * Null or blank is accepted to remove an existing number.
     */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Must be a valid international phone number")
    String phone
) {}
