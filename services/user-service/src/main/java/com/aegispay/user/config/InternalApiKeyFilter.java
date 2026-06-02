package com.aegispay.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates internal service-to-service calls via a shared secret header.
 *
 * <h3>How it works</h3>
 * Requests that carry {@code X-Internal-Api-Key: <secret>} bypass JWT validation
 * and are granted {@code ROLE_ADMIN} authority so they can reach endpoints
 * protected by {@code @PreAuthorize("hasRole('ADMIN')")}.
 *
 * <h3>When it's used</h3>
 * The AI Platform calls {@code PATCH /api/v1/users/{userId}/kyc/status} after
 * async KYC pipeline completes.  The call originates from the AI Platform's
 * background task executor — there is no user session and no Keycloak token
 * available at that point, so a shared secret is the right mechanism.
 *
 * <h3>Security posture</h3>
 * <ul>
 *   <li>The key is a long random string injected via the {@code USER_SERVICE_INTERNAL_API_KEY}
 *       environment variable (GCP Secret Manager via ESO in production).</li>
 *   <li>Service-to-service traffic never leaves the cluster network —
 *       the AI Platform calls the User Service directly (not via the public API Gateway),
 *       so the secret is not exposed to external networks.</li>
 *   <li>If the header is absent or the value does not match, this filter is a no-op:
 *       the request proceeds to the normal JWT validation chain unchanged.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    /** Header name carrying the shared secret. */
    public static final String HEADER = "X-Internal-Api-Key";

    private final UserServiceProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String key = request.getHeader(HEADER);

        if (key != null && !key.isBlank()) {
            String expected = props.getInternalApiKey();
            if (expected != null && expected.equals(key)) {
                // Valid internal call — grant ADMIN role so @PreAuthorize("hasRole('ADMIN')") passes.
                var auth = new UsernamePasswordAuthenticationToken(
                        "internal-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Internal API key accepted for {} {}", request.getMethod(), request.getRequestURI());
            } else {
                log.warn("Invalid X-Internal-Api-Key from {} {}", request.getMethod(), request.getRequestURI());
                // Let the request continue — it will fail at the JWT/authorization layer
                // with a proper 401/403, which is the correct behaviour for a bad key.
            }
        }

        chain.doFilter(request, response);
    }
}
