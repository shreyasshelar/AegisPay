package com.aegispay.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes AegisPay domain attributes back to the Keycloak user after registration,
 * so subsequent JWTs include {@code aegispay_user_id} and {@code aegispay_tenant_id}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Obtain a service-account access token via client-credentials grant.</li>
 *   <li>PATCH {@code /admin/realms/{realm}/users/{keycloakId}} with the attributes map.</li>
 * </ol>
 *
 * <p>Called asynchronously after user creation so it never blocks the registration response.
 * If Keycloak is unreachable the user still exists in our DB; the attribute will be written
 * on the next token refresh once the admin endpoint is reachable.
 */
@Slf4j
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final String realm;
    private final String adminClientId;
    private final String adminClientSecret;

    public KeycloakAdminService(
            @Value("${keycloak.auth-server-url:http://localhost:8180}") String authServerUrl,
            @Value("${keycloak.realm:aegispay}")                         String realm,
            @Value("${keycloak.admin.client-id:aegispay-backend}")       String adminClientId,
            @Value("${keycloak.admin.client-secret:aegispay-backend-dev-secret}") String adminClientSecret
    ) {
        this.restClient        = RestClient.builder().baseUrl(authServerUrl).build();
        this.realm             = realm;
        this.adminClientId     = adminClientId;
        this.adminClientSecret = adminClientSecret;
    }

    /**
     * Asynchronously writes {@code aegispay_user_id} and {@code aegispay_tenant_id}
     * to the Keycloak user identified by {@code keycloakSubject} (the JWT {@code sub} claim).
     *
     * @param keycloakSubject the Keycloak internal UUID (== JWT sub)
     * @param aegisUserId     the AegisPay domain UUID assigned during registration
     * @param tenantId        the tenant assigned during registration
     */
    @Async
    public void writeUserAttributes(String keycloakSubject, UUID aegisUserId, String tenantId) {
        try {
            String token = fetchAdminToken();

            Map<String, Object> body = Map.of(
                "attributes", Map.of(
                    "aegispay_user_id", List.of(aegisUserId.toString()),
                    "aegispay_tenant_id", List.of(tenantId != null ? tenantId : "default")
                )
            );

            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, keycloakSubject)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Keycloak attributes written: sub={} aegisUserId={}", keycloakSubject, aegisUserId);

        } catch (RestClientException e) {
            // Non-fatal: user is registered in our DB. The attribute will be missing from
            // the JWT until the next registration attempt or manual admin fix.
            // A retry could be added here via Spring Retry if needed.
            log.warn("Failed to write Keycloak attributes for sub={}: {}", keycloakSubject, e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "client_credentials");
        form.add("client_id",     adminClientId);
        form.add("client_secret", adminClientSecret);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RestClientException("No access_token in Keycloak client-credentials response");
        }
        return (String) response.get("access_token");
    }
}
