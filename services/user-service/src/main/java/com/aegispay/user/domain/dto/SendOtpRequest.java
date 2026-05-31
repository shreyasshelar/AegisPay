package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendOtpRequest(
        @NotBlank
        @Pattern(regexp = "\\+?[0-9]{10,15}", message = "phone must be 10-15 digits, optionally prefixed with +")
        String phone
) {}
