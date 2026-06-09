package com.aegispay.reconciliation.controller;

import com.aegispay.reconciliation.scheduler.ReconciliationScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationController}.
 *
 * <p>Uses Mockito to isolate from ClickHouse and Spring Batch — no external services needed.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationControllerTest {

    @Mock private ReconciliationScheduler scheduler;
    @Mock private JdbcTemplate            clickHouseJdbcTemplate;

    @InjectMocks
    private ReconciliationController controller;

    private static final LocalDate DATE = LocalDate.of(2024, 6, 1);

    // ── GET /reports/{date} ───────────────────────────────────────────────────

    @Test
    void getReport_returnsRowsAndTotal() {
        List<Map<String, Object>> rows = List.of(
                Map.of("break_id", "abc", "break_amount", 500),
                Map.of("break_id", "def", "break_amount", 200)
        );
        when(clickHouseJdbcTemplate.queryForList(anyString())).thenReturn(rows);
        when(clickHouseJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        ResponseEntity<Map<String, Object>> response =
                controller.getReport(DATE, 0, 200, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("reportDate")).isEqualTo("2024-06-01");
        assertThat(body.get("total")).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        List<?> breaks = (List<?>) body.get("breaks");
        assertThat(breaks).hasSize(2);
    }

    @Test
    void getReport_withBreakTypeFilter_includesInQuery() {
        when(clickHouseJdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        when(clickHouseJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        ResponseEntity<Map<String, Object>> response =
                controller.getReport(DATE, 0, 50, "AMOUNT_MISMATCH", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // SQL is constructed internally — verify the DB layer was called (filter wiring verified by integration test)
        verify(clickHouseJdbcTemplate, times(1)).queryForList(contains("AMOUNT_MISMATCH"));
    }

    @Test
    void getReport_withBreakStatusFilter_includesInQuery() {
        when(clickHouseJdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        when(clickHouseJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        controller.getReport(DATE, 0, 200, null, "OPEN");

        verify(clickHouseJdbcTemplate, times(1)).queryForList(contains("OPEN"));
    }

    @Test
    void getReport_nullTotalFromClickHouse_defaultsToZero() {
        when(clickHouseJdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        when(clickHouseJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        ResponseEntity<Map<String, Object>> response =
                controller.getReport(DATE, 0, 200, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("total")).isEqualTo(0L);
    }

    // ── GET /summary/{date} ───────────────────────────────────────────────────

    @Test
    void getSummary_returnsBreakdownAndTotals() {
        Map<String, Object> totals = Map.of(
                "total_breaks", 5L, "total_break_amount", 1500.0, "open_breaks", 3L
        );
        List<Map<String, Object>> breakdown = List.of(
                Map.of("break_type", "AMOUNT_MISMATCH", "break_count", 3L),
                Map.of("break_type", "MISSING_IN_STRIPE", "break_count", 2L)
        );
        when(clickHouseJdbcTemplate.queryForList(anyString())).thenReturn(breakdown);
        when(clickHouseJdbcTemplate.queryForMap(anyString())).thenReturn(totals);

        ResponseEntity<Map<String, Object>> response = controller.getSummary(DATE);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("reportDate")).isEqualTo("2024-06-01");
        assertThat(body.get("summary")).isEqualTo(totals);
        @SuppressWarnings("unchecked")
        List<?> bd = (List<?>) body.get("breakdown");
        assertThat(bd).hasSize(2);
    }

    // ── POST /run ─────────────────────────────────────────────────────────────

    @Test
    void triggerRun_withExplicitDate_launchesJobForThatDate() {
        JobExecution exec = mockJobExecution(BatchStatus.STARTED, 42L);
        when(scheduler.runFor(DATE)).thenReturn(exec);

        ResponseEntity<Map<String, Object>> response = controller.triggerRun(DATE);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("jobExecutionId")).isEqualTo(42L);
        assertThat(response.getBody().get("reportDate")).isEqualTo("2024-06-01");
        assertThat(response.getBody().get("status")).isEqualTo("STARTED");
    }

    @Test
    void triggerRun_noDate_defaultsToYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        JobExecution exec = mockJobExecution(BatchStatus.COMPLETED, 99L);
        when(scheduler.runFor(yesterday)).thenReturn(exec);

        ResponseEntity<Map<String, Object>> response = controller.triggerRun(null);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().get("reportDate")).isEqualTo(yesterday.toString());
        verify(scheduler).runFor(yesterday);
    }

    @Test
    void triggerRun_schedulerThrowsIllegalState_returns409() {
        when(scheduler.runFor(any())).thenThrow(new IllegalStateException("Already running"));

        ResponseEntity<Map<String, Object>> response = controller.triggerRun(DATE);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsKey("error");
    }

    // ── PATCH /breaks/{id}/status ─────────────────────────────────────────────

    @Test
    void updateBreakStatus_validStatus_executesUpdateAndReturns200() {
        doNothing().when(clickHouseJdbcTemplate).execute(anyString());

        ResponseEntity<Map<String, Object>> response =
                controller.updateBreakStatus("break-uuid-1", "CLOSED");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("newStatus")).isEqualTo("CLOSED");
        verify(clickHouseJdbcTemplate).execute(contains("CLOSED"));
        verify(clickHouseJdbcTemplate).execute(contains("break-uuid-1"));
    }

    @Test
    void updateBreakStatus_invalidStatus_returns400() {
        ResponseEntity<Map<String, Object>> response =
                controller.updateBreakStatus("break-uuid-1", "INVALID_STATUS");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsKey("error");
        verifyNoInteractions(clickHouseJdbcTemplate);
    }

    @Test
    void updateBreakStatus_allValidStatuses_accepted() {
        doNothing().when(clickHouseJdbcTemplate).execute(anyString());

        for (String status : List.of("OPEN", "IN_REVIEW", "CLOSED", "ESCALATED")) {
            ResponseEntity<Map<String, Object>> response =
                    controller.updateBreakStatus("break-1", status);
            assertThat(response.getStatusCode().value())
                    .as("Expected 200 for status=" + status)
                    .isEqualTo(200);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JobExecution mockJobExecution(BatchStatus status, long id) {
        JobExecution exec = mock(JobExecution.class);
        when(exec.getStatus()).thenReturn(status);
        when(exec.getId()).thenReturn(id);
        return exec;
    }
}
