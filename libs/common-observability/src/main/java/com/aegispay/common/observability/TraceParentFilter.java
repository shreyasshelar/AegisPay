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
 * Extracts the W3C traceparent header and binds it to MDC so every log line
 * for this request carries the trace context without manual propagation.
 *
 * Format: traceparent: 00-{traceId}-{spanId}-{flags}
 * We parse traceId and spanId for MDC convenience, but also store the full
 * header value for downstream forwarding.
 */
@Slf4j
@Order(1)
public class TraceParentFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_PARENT = "traceparent";
    public static final String HEADER_TRACE_STATE  = "tracestate";

    public static final String MDC_TRACE_PARENT = "traceParent";
    public static final String MDC_TRACE_ID     = "traceId";
    public static final String MDC_SPAN_ID      = "spanId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceParent = request.getHeader(HEADER_TRACE_PARENT);

        if (traceParent != null && !traceParent.isBlank()) {
            MDC.put(MDC_TRACE_PARENT, traceParent);
            parseAndBindTraceComponents(traceParent);
            response.setHeader(HEADER_TRACE_PARENT, traceParent);

            String traceState = request.getHeader(HEADER_TRACE_STATE);
            if (traceState != null) {
                response.setHeader(HEADER_TRACE_STATE, traceState);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_PARENT);
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        }
    }

    private void parseAndBindTraceComponents(String traceParent) {
        // traceparent = 00-<traceId>-<parentSpanId>-<flags>
        String[] parts = traceParent.split("-");
        if (parts.length >= 4) {
            MDC.put(MDC_TRACE_ID, parts[1]);
            MDC.put(MDC_SPAN_ID, parts[2]);
        }
    }
}
