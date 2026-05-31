package com.aegispay.user.domain.dto;

/**
 * Thin wrapper returned by {@link com.aegispay.user.service.UserService#register} so
 * that the controller can emit the semantically correct HTTP status code:
 * <ul>
 *   <li>{@code created = true}  → HTTP 201 Created  (new account was provisioned)</li>
 *   <li>{@code created = false} → HTTP 200 OK       (idempotent: account already existed)</li>
 * </ul>
 */
public record RegistrationResult(UserResponse user, boolean created) {}
