package com.aegispay.reconciliation.controller;

import com.aegispay.reconciliation.scheduler.ReconciliationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for the reconciliation service.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/v1/reconciliation/reports/{date}}  — query breaks for a given date
 *   <li>{@code GET  /api/v1/reconciliation/summary/{date}}  — aggregated summary stats
 *   <li>{@code POST /api/v1/reconciliation/run}             — trigger an ad-hoc run
 * </ul>
 *
 * <p>All write operations require the {@code BACK_OFFICE} or {@code ADMIN} role.
 * Read operations require at least {@code MERCHANT_OPS}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationScheduler scheduler;
    private final JdbcTemplate clickHouseJdbcTemplate;

    // ── Reports ───────────────────────────────────────────────────────────────

    /**
     * Returns every reconciliation break for a given report date.
     *
     * <p>Example: {@code GET /api/v1/reconciliation/reports/2024-01-15}
     */
    @GetMapping("/reports/{date}")
    @PreAuthorize("hasAnyRole('MERCHANT_OPS','BACK_OFFICE','ADMIN')")
    public ResponseEntity<Map<String, Object>> getReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String breakType,
            @RequestParam(required = false) String breakStatus) {

        String sql = buildSelectSql(date, breakType, breakStatus, limit, offset);
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(sql);

        String countSql = buildCountSql(date, breakType, breakStatus);
        Long total = clickHouseJdbcTemplate.queryForObject(countSql, Long.class);

        return ResponseEntity.ok(Map.of(
                "reportDate", date.toString(),
                "total", total != null ? total : 0,
                "offset", offset,
                "limit", limit,
                "breaks", rows
        ));
    }

    /**
     * Returns an aggregated summary (break counts by type, total break amount) for a date.
     *
     * <p>Example: {@code GET /api/v1/reconciliation/summary/2024-01-15}
     */
    @GetMapping("/summary/{date}")
    @PreAuthorize("hasAnyRole('MERCHANT_OPS','BACK_OFFICE','ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        String sql = """
                SELECT
                    break_type,
                    break_status,
                    count()              AS break_count,
                    sum(break_amount)    AS total_break_amount,
                    currency
                FROM aegispay_analytics.reconciliation_breaks
                WHERE report_date = '%s'
                GROUP BY break_type, break_status, currency
                ORDER BY break_count DESC
                """.formatted(date);

        List<Map<String, Object>> breakdown = clickHouseJdbcTemplate.queryForList(sql);

        // Overall totals
        String totalSql = """
                SELECT
                    count()           AS total_breaks,
                    sum(break_amount) AS total_break_amount,
                    countIf(break_status = 'OPEN') AS open_breaks
                FROM aegispay_analytics.reconciliation_breaks
                WHERE report_date = '%s'
                """.formatted(date);

        Map<String, Object> totals = clickHouseJdbcTemplate.queryForMap(totalSql);

        return ResponseEntity.ok(Map.of(
                "reportDate", date.toString(),
                "summary", totals,
                "breakdown", breakdown
        ));
    }

    // ── Manual trigger ────────────────────────────────────────────────────────

    /**
     * Triggers an ad-hoc reconciliation run for the given date.
     *
     * <p>Example: {@code POST /api/v1/reconciliation/run?date=2024-01-15}
     *
     * <p>If {@code date} is omitted, defaults to yesterday.
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('BACK_OFFICE','ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerRun(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        log.info("Manual reconciliation run requested for date={} ", targetDate);

        try {
            JobExecution execution = scheduler.runFor(targetDate);
            BatchStatus status = execution.getStatus();
            return ResponseEntity.accepted().body(Map.of(
                    "jobExecutionId", execution.getId(),
                    "reportDate", targetDate.toString(),
                    "status", status.name(),
                    "message", "Reconciliation job " + (status == BatchStatus.STARTED ? "started" : status.name().toLowerCase())
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "reportDate", targetDate.toString(),
                    "error", e.getMessage()
            ));
        }
    }

    // ── Update break status ───────────────────────────────────────────────────

    /**
     * Mark a break as resolved (CLOSED) or under investigation (IN_REVIEW).
     *
     * <p>Example: {@code PATCH /api/v1/reconciliation/breaks/{breakId}/status?status=CLOSED}
     *
     * <p>Note: ClickHouse uses CollapsingMergeTree-style mutations for updates.
     * For simplicity, this service inserts a new row with updated status rather than
     * an actual UPDATE (ClickHouse UPDATE is async and expensive on MergeTree).
     */
    @PatchMapping("/breaks/{breakId}/status")
    @PreAuthorize("hasAnyRole('BACK_OFFICE','ADMIN')")
    public ResponseEntity<Map<String, Object>> updateBreakStatus(
            @PathVariable String breakId,
            @RequestParam String status) {

        if (!List.of("OPEN", "IN_REVIEW", "CLOSED", "ESCALATED").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid status. Allowed: OPEN, IN_REVIEW, CLOSED, ESCALATED"
            ));
        }

        // ClickHouse ALTER TABLE UPDATE is eventual — this is best-effort for fintech ops dashboards
        String sql = """
                ALTER TABLE aegispay_analytics.reconciliation_breaks
                UPDATE break_status = '%s'
                WHERE break_id = '%s'
                """.formatted(status, breakId);

        clickHouseJdbcTemplate.execute(sql);
        log.info("Break {} status updated to {}", breakId, status);

        return ResponseEntity.ok(Map.of(
                "breakId", breakId,
                "newStatus", status,
                "message", "Status update submitted (ClickHouse async mutation)"
        ));
    }

    // ── SQL helpers ───────────────────────────────────────────────────────────

    private String buildSelectSql(LocalDate date, String breakType, String breakStatus, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM aegispay_analytics.reconciliation_breaks
                WHERE report_date = '%s'
                """.formatted(date));

        if (breakType != null && !breakType.isBlank()) {
            sql.append(" AND break_type = '").append(breakType).append("'");
        }
        if (breakStatus != null && !breakStatus.isBlank()) {
            sql.append(" AND break_status = '").append(breakStatus).append("'");
        }
        sql.append(" ORDER BY break_amount DESC LIMIT ").append(limit).append(" OFFSET ").append(offset);
        return sql.toString();
    }

    private String buildCountSql(LocalDate date, String breakType, String breakStatus) {
        StringBuilder sql = new StringBuilder("""
                SELECT count()
                FROM aegispay_analytics.reconciliation_breaks
                WHERE report_date = '%s'
                """.formatted(date));
        if (breakType != null && !breakType.isBlank()) {
            sql.append(" AND break_type = '").append(breakType).append("'");
        }
        if (breakStatus != null && !breakStatus.isBlank()) {
            sql.append(" AND break_status = '").append(breakStatus).append("'");
        }
        return sql.toString();
    }
}
