package com.aegispay.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the AegisPay domain UUID for a user whose JWT does not yet carry the
 * {@code aegispay_user_id} claim.
 *
 * <p>This happens on the first session after a social (Google/Microsoft) SSO login:
 * Keycloak issues the access token before {@code auth.ts} finishes writing the
 * {@code aegispay_user_id} attribute back to the Keycloak user profile.  Subsequent
 * sessions (after Keycloak has cached the attribute) contain the claim and bypass
 * this lookup entirely.
 *
 * <p>The lookup calls {@code GET /api/v1/users/me} on User Service with the user's
 * own Bearer token.  User Service's bootstrap path handles the absent-claim case by
 * looking up the user by their Keycloak {@code sub} ({@code external_id} column), so
 * the call succeeds as long as the user was provisioned by {@code /register} earlier
 * in the same session (which {@code auth.ts} guarantees before any other API call).
 */
@Slf4j
@Service
public class UserLookupService {

    private final RestClient userServiceRestClient;

    public UserLookupService(@Qualifier("userServiceRestClient") RestClient userServiceRestClient) {
        this.userServiceRestClient = userServiceRestClient;
    }

    /**
     * Calls User Service {@code /me} with the caller's Bearer token and extracts the
     * AegisPay domain UUID from the response.
     *
     * @param bearerToken  Raw JWT token value ({@code jwt.getTokenValue()}).
     * @return             The AegisPay domain UUID, or {@link Optional#empty()} if User
     *                     Service is unreachable or the user has not been provisioned yet.
     */
    @SuppressWarnings("unchecked")
    public Optional<UUID> resolveUserId(String bearerToken) {
        try {
            Map<String, Object> body = userServiceRestClient.get()
                    .uri("/api/v1/users/me")
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                log.warn("User /me returned null body");
                return Optional.empty();
            }

            // ApiResponse<UserResponse> shape: {"success": true, "data": {"id": "<uuid>", ...}}
            Object data = body.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                log.warn("User /me response has no 'data' object: {}", body.keySet());
                return Optional.empty();
            }

            Object id = dataMap.get("id");
            if (id == null) {
                log.warn("User /me 'data' has no 'id' field");
                return Optional.empty();
            }

            return Optional.of(UUID.fromString(id.toString()));

        } catch (RestClientResponseException e) {
            log.warn("User /me returned HTTP {}: {}", e.getStatusCode().value(), e.getMessage());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("User /me call failed (User Service unreachable?): {}", e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("User /me returned malformed UUID: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
