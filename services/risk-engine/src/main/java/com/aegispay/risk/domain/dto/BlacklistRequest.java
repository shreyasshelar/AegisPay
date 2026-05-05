package com.aegispay.risk.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Value;

@Value
public class BlacklistRequest {
    @NotBlank
    @Pattern(regexp = "USER|IBAN|IP", message = "entityType must be USER, IBAN, or IP")
    String entityType;

    @NotBlank
    String entityValue;

    String reason;
}
