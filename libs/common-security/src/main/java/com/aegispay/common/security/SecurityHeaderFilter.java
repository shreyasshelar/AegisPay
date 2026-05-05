package com.aegispay.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Propagates traceparent and X-Correlation-ID into the response so callers
 * can correlate their upstream trace with this service's span.
 */
@Slf4j
public class SecurityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader("X-Correlation-ID");
        String traceParent   = request.getHeader("traceparent");

        if (correlationId != null) {
            response.setHeader("X-Correlation-ID", correlationId);
        }
        if (traceParent != null) {
            response.setHeader("traceparent", traceParent);
        }

        filterChain.doFilter(request, response);
    }
}
