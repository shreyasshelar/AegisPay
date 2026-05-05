package com.aegispay.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates MDC with request-scoped metadata (method, path, remoteAddr) so
 * every log line within a request automatically carries structured context.
 *
 * Runs after CorrelationIdFilter and TraceParentFilter (Order 2) so all
 * trace/correlation values are already in MDC when this filter executes.
 *
 * NOTE: remoteAddr is logged as-is — no masking needed as it is not PII
 * under the fintech regulatory model used here (device fingerprint is stored
 * separately and masked there).
 */
@Slf4j
@Order(2)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String MDC_HTTP_METHOD  = "httpMethod";
    private static final String MDC_REQUEST_PATH = "requestPath";
    private static final String MDC_REMOTE_ADDR  = "remoteAddr";
    private static final String MDC_SERVICE_NAME = "serviceName";

    private final String serviceName;

    public MdcLoggingFilter(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
        MDC.put(MDC_REMOTE_ADDR, request.getRemoteAddr());
        MDC.put(MDC_SERVICE_NAME, serviceName);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("method={} path={} status={} elapsed={}ms",
                     request.getMethod(), request.getRequestURI(),
                     response.getStatus(), elapsed);

            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_REMOTE_ADDR);
            MDC.remove(MDC_SERVICE_NAME);
        }
    }
}
