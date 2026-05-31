package com.aegispay.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Creates a {@link RestClient} bean that the KYC async pipeline uses to call
 * back into the User Service after processing completes.
 *
 * <p>The client targets the User Service directly (bypassing the API Gateway)
 * because:
 * <ul>
 *   <li>The callback carries an {@code X-Internal-Api-Key} header rather than a
 *       user JWT — routing through the gateway would require the gateway to pass
 *       that header through and have User Service accept it at the JWT layer.</li>
 *   <li>The callback result (KYC decision) is produced by the same service
 *       making the call — there is no user session to forward.</li>
 * </ul>
 *
 * <h3>Why JdkClientHttpRequestFactory instead of SimpleClientHttpRequestFactory</h3>
 * <p>{@link org.springframework.http.client.SimpleClientHttpRequestFactory} delegates to
 * {@link java.net.HttpURLConnection}, which does <em>not</em> support the
 * {@code PATCH} verb — calling {@code setRequestMethod("PATCH")} throws a
 * {@link java.net.ProtocolException} before any network I/O occurs.  This
 * silently fails every callback:
 * <ul>
 *   <li>{@code markDocumentSubmitted} — preliminary {@code DOCUMENT_SUBMITTED}
 *       callback, called synchronously before 202 is returned.</li>
 *   <li>{@code sendCallback} / {@code sendFailureCallback} — final async result
 *       ({@code APPROVED} / {@code REJECTED}), preventing User Service from
 *       publishing {@code KycStatusChangedEvent} → Kafka → WebSocket notification.</li>
 * </ul>
 *
 * <p>{@link JdkClientHttpRequestFactory} wraps Java 21's {@link java.net.http.HttpClient}
 * (GA since JDK 11, JEP 321) which fully supports all HTTP verbs including
 * {@code PATCH}.  No new Maven dependency is required.
 *
 * <h3>Timeouts</h3>
 * <ul>
 *   <li>{@code connectTimeout = 2 s} — fail fast if User Service TCP socket cannot
 *       be opened; returned as {@link org.springframework.web.client.ResourceAccessException}
 *       caught in {@code KycDocumentService.postToUserService()}.</li>
 *   <li>{@code readTimeout = 5 s} — fail fast if User Service is slow to respond;
 *       same exception handling applies.</li>
 * </ul>
 *
 * <p>The bean is qualified as {@code "userServiceRestClient"} to avoid
 * ambiguity if other RestClient beans are added in future.
 */
@Configuration
public class KycCallbackRestClientConfig {

    /** Connect timeout: fail fast if User Service TCP socket cannot be opened. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** Read timeout: fail fast if User Service is slow to send a response. */
    private static final int READ_TIMEOUT_MS = 5_000;

    @Bean("userServiceRestClient")
    public RestClient userServiceRestClient(AiPlatformProperties props) {
        // java.net.http.HttpClient (Java 11+) supports all HTTP verbs including PATCH.
        // SimpleClientHttpRequestFactory (HttpURLConnection) does NOT — it throws
        // ProtocolException for PATCH, silently killing every KYC callback.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(props.getUserService().getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
