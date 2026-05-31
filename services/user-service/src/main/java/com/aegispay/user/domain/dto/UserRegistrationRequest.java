package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UserRegistrationRequest(

    @NotBlank @Email(message = "Must be a valid email address")
    String email,

    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Must be a valid international phone number")
    String phone,

    @NotBlank @Size(min = 1, max = 100)
    String firstName,

    @NotBlank @Size(min = 1, max = 100)
    String lastName,

    /** Optional tenant context (e.g. merchant tenant ID for sub-tenant users). */
    String tenantId
) {}
