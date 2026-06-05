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
 * so subsequent JWTs include {@code aegispay_user_id}, {@code aegispay_tenant_id},
 * and {@code aegispay_role}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Obtain an admin access token via password grant against the <b>master realm</b>
 *       ({@code admin-cli} client + admin username/password). Using the master-realm admin
 *       token is required because service-account tokens from a non-master realm do not
 *       automatically include {@code resource_access.realm-management} roles in the JWT
 *       claims, causing Keycloak's admin REST API to return 403 even when the service
 *       account user has {@code manage-users} assigned. The master-realm admin token has
 *       unconditional admin access to all realms.</li>
 *   <li>PUT {@code /admin/realms/{realm}/users/{keycloakId}} with the attributes map.</li>
 * </ol>
 *
 * <p>Called asynchronously after user creation so it never blocks the registration response.
 * If Keycloak is unreachable the user still exists in our DB; the attribute will be written
 * on the next registration attempt (idempotent path in {@code UserService.register}).
 *
 * <p><b>K8s note</b>: {@code KEYCLOAK_URL} must point to the internal cluster URL
 * ({@code http://keycloak.aegispay-infra.svc.cluster.local:8080}) NOT {@code localhost:8180}.
 * The configmap injects this via the {@code aegispay.keycloakInternalBaseUrl} Helm helper.
 */
@Slf4j
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final String realm;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakAdminService(
            @Value("${keycloak.auth-server-url:http://localhost:8180}") String authServerUrl,
            @Value("${keycloak.realm:aegispay}")                         String realm,
            @Value("${keycloak.admin.username:admin}")                   String adminUsername,
            @Value("${keycloak.admin.password:}")                        String adminPassword
    ) {
        this.restClient    = RestClient.builder().baseUrl(authServerUrl).build();
        this.realm         = realm;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    /**
     * Asynchronously writes AegisPay domain attributes to the Keycloak user identified
     * by {@code keycloakSubject} (JWT {@code sub} claim).
     *
     * <p>Attributes written:
     * <ul>
     *   <li>{@code aegispay_user_id} — AegisPay domain UUID; maps to JWT claim via Keycloak mapper</li>
     *   <li>{@code aegispay_tenant_id} — tenant for the user</li>
     *   <li>{@code aegispay_role} — defaults to {@code CUSTOMER}; maps to JWT claim via Keycloak mapper.
     *       Without this, social-login users have no role claim in the JWT and some @PreAuthorize
     *       checks fall back to the SecurityConfig default (ROLE_CUSTOMER) rather than reading the
     *       explicit claim. Writing it explicitly ensures consistency across all login methods.</li>
     * </ul>
     *
     * @param keycloakSubject the Keycloak internal UUID (== JWT sub)
     * @param aegisUserId     the AegisPay domain UUID assigned during registration
     * @param tenantId        the tenant assigned during registration
     * @param role            the AegisPay role (e.g. "CUSTOMER") — use "CUSTOMER" for social login
     */
    @Async
    public void writeUserAttributes(String keycloakSubject, UUID aegisUserId,
                                    String tenantId, String role) {
        try {
            String token = fetchAdminToken();

            Map<String, Object> body = Map.of(
                "attributes", Map.of(
                    "aegispay_user_id",   List.of(aegisUserId.toString()),
                    "aegispay_tenant_id", List.of(tenantId != null ? tenantId : "default"),
                    "aegispay_role",      List.of(role != null ? role.toUpperCase() : "CUSTOMER")
                )
            );

            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, keycloakSubject)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak attributes written: sub={} aegisUserId={} role={}", keycloakSubject, aegisUserId, role);

        } catch (RestClientException e) {
            log.warn("Failed to write Keycloak attributes for sub={}: {} " +
                     "(user IS registered in DB — check KEYCLOAK_URL env var points to internal cluster URL)",
                     keycloakSubject, e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Fetches a Keycloak admin token using the <b>master realm password grant</b>.
     *
     * <p>We use the master realm admin user ({@code admin-cli} client, password grant) rather
     * than a service-account client_credentials grant because:
     * <ul>
     *   <li>Service-account tokens issued in the {@code aegispay} realm do not include
     *       {@code resource_access.realm-management} roles in JWT claims by default, even
     *       when the SA user has {@code manage-users} assigned — Keycloak requires a custom
     *       audience/scope mapper to propagate them, which is a complex Keycloak-internal
     *       configuration step prone to breaking on realm reimport.</li>
     *   <li>The master-realm admin token unconditionally passes admin REST API checks for
     *       all realms, eliminating the 403 / unknown_error class of failures.</li>
     * </ul>
     * The admin password is injected from GCP Secret Manager via ESO →
     * {@code KEYCLOAK_ADMIN_PASSWORD} env var so it never lives in plaintext in the repo.
     */
    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id",  "admin-cli");
        form.add("username",   adminUsername);
        form.add("password",   adminPassword);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RestClientException("No access_token in Keycloak master-realm admin response");
        }
        return (String) response.get("access_token");
    }
}