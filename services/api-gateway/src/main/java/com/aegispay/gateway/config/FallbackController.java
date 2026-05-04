package com.aegispay.gateway.config;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker fallback endpoint. Spring Cloud Gateway forwards to this
 * controller when a downstream service is unavailable and the circuit is open.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/service-unavailable")
    public Mono<ApiResponse<Void>> serviceUnavailable() {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("SERVICE_UNAVAILABLE")
                .message("The requested service is temporarily unavailable. Please retry in a moment.")
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                .build();
        return Mono.just(ApiResponse.error(error));
    }
}
