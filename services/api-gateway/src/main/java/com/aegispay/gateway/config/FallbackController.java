package com.aegispay.gateway.config;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker fallback endpoint. Spring Cloud Gateway forwards to this
 * controller when a downstream service is unavailable and the circuit is open.
 *
 * <p>Response headers added:
 * <ul>
 *   <li>{@code Retry-After: 30} — matches the {@code waitDurationInOpenState} configured
 *       in {@link CircuitBreakerCustomizerConfig}. Tells clients (and browsers' fetch)
 *       the minimum back-off before retrying. The frontend reads this header to drive
 *       its retry button delay.</li>
 *   <li>{@code X-Failed-Service} — the route ID that tripped the circuit, useful for
 *       client-side error reporting and server-side log correlation.</li>
 * </ul>
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /** Must match the longest {@code waitDurationInOpenState} across all CB instances. */
    private static final String RETRY_AFTER_SECONDS = "30";

    @RequestMapping("/service-unavailable")
    public Mono<ResponseEntity<ApiResponse<Void>>> serviceUnavailable(ServerWebExchange exchange) {
        // Extract the originating route ID so the caller knows which service is down.
        // The attribute is present when the CB filter forwards here; absent on direct calls.
        String failedService = "unknown";
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            failedService = route.getId();
        }

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("SERVICE_UNAVAILABLE")
                .message("The requested service is temporarily unavailable. Please retry in a moment.")
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                .build();

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .header("X-Failed-Service", failedService)
                .body(ApiResponse.error(error)));
    }
}
