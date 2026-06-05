package com.aegispay.notification.client;

import com.aegispay.notification.domain.document.UserContactDocument;
import com.aegispay.notification.repository.UserContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Fallback HTTP client that lazily provisions a {@link UserContactDocument} in MongoDB
 * when the document is missing because the {@code user.registered} Kafka event was
 * delayed or not yet consumed by the notification-service.
 *
 * <h3>When this fires</h3>
 * <ol>
 *   <li>User registers → {@code user.registered} published via outbox → Kafka.</li>
 *   <li>Notification-service's {@code UserRegisteredConsumer} hasn't processed it yet
 *       (e.g. pod restarting) AND a transaction notification fires in the same window.</li>
 *   <li>{@code TransactionStatusConsumer.resolveContact()} finds no document in MongoDB
 *       → calls {@link #fetchAndProvision(String)} → HTTP call to user-service →
 *       saves a new {@code UserContactDocument} → notification delivered.</li>
 * </ol>
 *
 * <h3>Authentication</h3>
 * Calls {@code GET /api/v1/users/{userId}/internal/contact} with
 * {@code X-Internal-Api-Key} header.  {@code InternalApiKeyFilter} in user-service
 * converts this to {@code ROLE_ADMIN}, allowing the endpoint to return the full
 * (unmasked) email and phone required for notification delivery.
 *
 * <h3>Key guarantee</h3>
 * This path only fires when the normal Kafka-based provisioning hasn't happened yet.
 * For the overwhelming majority of users, {@link UserContactRepository#findById} returns
 * a result immediately (hot path) and this client is never called.
 */
@Slf4j
@Service
public class UserServiceFallbackClient {

    /** Header name for the internal service-to-service shared secret. */
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate            restTemplate;
    private final UserContactRepository   contactRepository;
    private final String                  userServiceBaseUrl;
    private final String                  internalApiKey;

    public UserServiceFallbackClient(
            RestTemplate restTemplate,
            UserContactRepository contactRepository,
            @Value("${aegispay.user-service.base-url:http://localhost:8081}")
                String baseUrl,
            @Value("${aegispay.user-service.internal-api-key:aegispay-internal-dev-key}")
                String internalApiKey
    ) {
        this.restTemplate     = restTemplate;
        this.contactRepository = contactRepository;
        this.userServiceBaseUrl = baseUrl.replaceAll("/$", "");
        this.internalApiKey   = internalApiKey;
    }

    // ── Response envelope types ───────────────────────────────────────────────

    private record ContactData(
            String id,
            String email,
            String phoneNumber,
            boolean smsNotificationsEnabled) {}

    private record ContactEnvelope(boolean success, ContactData data) {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches full (unmasked) contact details for {@code userId} from user-service
     * and upserts a {@link UserContactDocument} in MongoDB.
     *
     * <p>This method is safe to call concurrently — MongoDB's document-level
     * atomicity ensures the upsert is idempotent if two Kafka consumers race on
     * the same {@code userId}.
     *
     * @param userId the AegisPay domain UUID string
     * @return the upserted {@link UserContactDocument}, or {@code null} if
     *         user-service is unreachable or returns a non-2xx response
     */
    public UserContactDocument fetchAndProvision(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_KEY_HEADER, internalApiKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    userServiceBaseUrl + "/api/v1/users/" + userId + "/internal/contact",
                    HttpMethod.GET,
                    request,
                    ContactEnvelope.class);

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || !response.getBody().success()
                    || response.getBody().data() == null) {
                log.warn("fetchAndProvision: user-service returned empty/error envelope "
                        + "for userId={}", userId);
                return null;
            }

            ContactData data = response.getBody().data();

            // Upsert: preserve any fields already set (e.g. phone from a prior
            // UserContactUpdatedEvent) rather than wholesale-replacing the document.
            UserContactDocument contact = contactRepository.findById(userId)
                    .orElseGet(() -> UserContactDocument.builder()
                            .userId(userId)
                            .smsNotificationsEnabled(false)
                            .build());

            if (data.email() != null && !data.email().isBlank()) {
                contact.setEmail(data.email());
            }
            // Only set phone from this fallback if not already on file; the
            // UserContactUpdatedConsumer (phone OTP verification path) is authoritative.
            if (data.phoneNumber() != null && !data.phoneNumber().isBlank()
                    && contact.getPhoneNumber() == null) {
                contact.setPhoneNumber(data.phoneNumber());
                contact.setSmsNotificationsEnabled(data.smsNotificationsEnabled());
            }
            contact.setUpdatedAt(Instant.now());
            contactRepository.save(contact);

            log.info("UserContactDocument lazily provisioned via user-service fallback: "
                    + "userId={} hasEmail={} hasPhone={}",
                    userId, data.email() != null, data.phoneNumber() != null);
            return contact;

        } catch (HttpClientErrorException.NotFound e) {
            // User doesn't exist (404) — should not happen since the event came from
            // the user's own transaction, but guard against stale events.
            log.warn("fetchAndProvision: user not found in user-service: userId={}", userId);
        } catch (RestClientException e) {
            log.warn("fetchAndProvision: user-service unreachable for userId={}: {}",
                    userId, e.getMessage());
        } catch (Exception e) {
            log.warn("fetchAndProvision: unexpected error for userId={}: {}",
                    userId, e.getMessage());
        }
        return null;
    }
}
