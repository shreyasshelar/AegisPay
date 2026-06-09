package com.aegispay.reconciliation.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationScheduler}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The correct reportDate parameter is set on the job
 *   <li>The job is launched exactly once per call
 *   <li>JobExecution is propagated back to callers
 *   <li>Concurrent execution conflicts are detected and wrapped
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private Job         reconciliationJob;

    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReconciliationScheduler(jobLauncher, reconciliationJob);
    }

    @Test
    void runFor_launchesJobWithCorrectDateParameter() throws Exception {
        LocalDate   date      = LocalDate.of(2024, 6, 1);
        JobExecution execution = mockJobExecution(BatchStatus.STARTED);
        when(jobLauncher.run(eq(reconciliationJob), any(JobParameters.class)))
                .thenReturn(execution);

        JobExecution result = scheduler.runFor(date);

        // Verify launch was called exactly once
        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(reconciliationJob), captor.capture());

        String reportDate = captor.getValue().getString("reportDate");
        assertThat(reportDate).isEqualTo("2024-06-01");
        assertThat(result).isSameAs(execution);
    }

    @Test
    void runFor_differentDates_producesDistinctJobParameters() throws Exception {
        LocalDate    d1   = LocalDate.of(2024, 6, 1);
        LocalDate    d2   = LocalDate.of(2024, 6, 2);
        JobExecution exec = mockJobExecution(BatchStatus.COMPLETED);
        when(jobLauncher.run(any(), any())).thenReturn(exec);

        scheduler.runFor(d1);
        scheduler.runFor(d2);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher, times(2)).run(any(), captor.capture());

        assertThat(captor.getAllValues().get(0).getString("reportDate")).isEqualTo("2024-06-01");
        assertThat(captor.getAllValues().get(1).getString("reportDate")).isEqualTo("2024-06-02");
    }

    @Test
    void runFor_jobAlreadyRunning_throwsIllegalStateException() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenThrow(new org.springframework.batch.core.repository.JobExecutionAlreadyRunningException("running"));

        assertThatThrownBy(() -> scheduler.runFor(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void runFor_jobAlreadyCompleteForSameInstance_throwsIllegalStateException() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenThrow(new org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException("complete"));

        assertThatThrownBy(() -> scheduler.runFor(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runDailyReconciliation_usesYesterdayDate() throws Exception {
        LocalDate   yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        JobExecution exec     = mockJobExecution(BatchStatus.STARTED);
        when(jobLauncher.run(any(), any())).thenReturn(exec);

        scheduler.runDailyReconciliation();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(), captor.capture());
        assertThat(captor.getValue().getString("reportDate")).isEqualTo(yesterday.toString());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private JobExecution mockJobExecution(BatchStatus status) {
        JobExecution exec = mock(JobExecution.class);
        when(exec.getStatus()).thenReturn(status);
        when(exec.getId()).thenReturn(1L);
        return exec;
    }
}
