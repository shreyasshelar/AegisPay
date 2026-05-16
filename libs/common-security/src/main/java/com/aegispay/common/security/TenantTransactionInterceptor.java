package com.aegispay.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AOP aspect that fires {@code SET LOCAL app.tenant_id = '<value>'} on the current
 * PostgreSQL connection at the start of every {@link org.springframework.transaction.annotation.Transactional}
 * method boundary.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Spring opens a transaction (connection obtained from pool).</li>
 *   <li>This aspect fires {@code @Around} the join-point, <em>after</em> the transaction
 *       is already active, so the {@code SET LOCAL} applies to the current physical
 *       connection and is automatically rolled back when the transaction ends.</li>
 *   <li>If no tenant is in {@link ActorContext} (e.g. internal async jobs), the GUC is
 *       set to {@code "system"} — which matches the sentinel value used in migrations,
 *       bypassing tenant isolation for background processing.</li>
 * </ol>
 *
 * <p><b>Thread safety:</b> {@link ActorContext} is ThreadLocal; {@code SET LOCAL} is
 * connection-scoped. Both reset automatically after the transaction and request end.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class TenantTransactionInterceptor {

    private static final String SYSTEM_TENANT = "system";
    private static final String SET_TENANT_SQL = "SET LOCAL app.tenant_id = ?";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Intercept any {@code @Transactional} method and inject the tenant GUC before
     * the body executes.  The pointcut covers both the annotation and its meta-usage
     * via {@code @Service}, etc.
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantContext(ProceedingJoinPoint pjp) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            String tenantId = resolveTenantId();
            setTenantGuc(tenantId);
        }
        return pjp.proceed();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String resolveTenantId() {
        if (ActorContext.isPresent()) {
            String tid = ActorContext.get().getTenantId();
            if (tid != null && !tid.isBlank()) {
                return tid;
            }
        }
        return SYSTEM_TENANT;
    }

    private void setTenantGuc(String tenantId) {
        try {
            jdbcTemplate.update(SET_TENANT_SQL, tenantId);
            if (log.isTraceEnabled()) {
                log.trace("RLS tenant set: {}", tenantId);
            }
        } catch (Exception ex) {
            // Non-fatal — log and continue.  If the DB doesn't support the GUC the
            // migration already failed; this is a defence-in-depth path.
            log.warn("Could not set app.tenant_id GUC: {}", ex.getMessage());
        }
    }
}
