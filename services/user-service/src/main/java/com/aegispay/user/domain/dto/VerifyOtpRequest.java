package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank
        @Pattern(regexp = "\\+?[0-9]{10,15}", message = "phone must be 10-15 digits, optionally prefixed with +")
        String phone,

        @NotBlank
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
        @Pattern(regexp = "[0-9]{6}", message = "OTP must be 6 digits")
        String otp
) {}
