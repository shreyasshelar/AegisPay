package com.aegispay.gateway.config;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global exception handler for the reactive gateway.
 *
 * Intercepts exceptions before Spring's DefaultErrorWebExceptionHandler so all
 * error responses are consistently wrapped in ApiResponse<Void>.
 *
 * Ordering -1 puts this just ahead of Spring's own handler at Ordered.LOWEST_PRECEDENCE.
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GlobalWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        String errorCode = resolveErrorCode(ex, status);
        String message = resolveMessage(ex, status);

        if (status.is5xxServerError()) {
            log.error("Unhandled gateway error: path={} status={} error={}",
                    exchange.getRequest().getPath().value(), status, ex.getMessage(), ex);
        } else {
            log.warn("Client error: path={} status={} error={}",
                    exchange.getRequest().getPath().value(), status, ex.getMessage());
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .httpStatus(status.value())
                .build();

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.error(error));
        } catch (JsonProcessingException e) {
            body = fallbackJson(status.value(), errorCode);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    // ── Resolution helpers ─────────────────────────────────────────────────────

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof AuthenticationException) return HttpStatus.UNAUTHORIZED;
        if (ex instanceof AccessDeniedException) return HttpStatus.FORBIDDEN;
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.resolve(rse.getStatusCode().value()) != null
                    ? HttpStatus.valueOf(rse.getStatusCode().value())
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveErrorCode(Throwable ex, HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case TOO_MANY_REQUESTS -> "RATE_LIMIT_EXCEEDED";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case BAD_GATEWAY -> "BAD_GATEWAY";
            case GATEWAY_TIMEOUT -> "GATEWAY_TIMEOUT";
            default -> "INTERNAL_SERVER_ERROR";
        };
    }

    private String resolveMessage(Throwable ex, HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Authentication is required to access this resource.";
            case FORBIDDEN -> "You do not have permission to access this resource.";
            case NOT_FOUND -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED -> "HTTP method not allowed for this endpoint.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please slow down and retry after the reset window.";
            case SERVICE_UNAVAILABLE -> "The requested service is temporarily unavailable. Please retry in a moment.";
            case BAD_GATEWAY -> "An upstream service returned an invalid response.";
            case GATEWAY_TIMEOUT -> "An upstream service did not respond in time.";
            default -> "An unexpected error occurred. Please try again later.";
        };
    }

    private byte[] fallbackJson(int httpStatus, String errorCode) {
        return ("{\"success\":false,\"error\":{\"httpStatus\":" + httpStatus
                + ",\"errorCode\":\"" + errorCode + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
