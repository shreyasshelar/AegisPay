package com.aegispay.reconciliation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Triggers the Spring Batch reconciliation job on a configurable cron schedule.
 *
 * <p>Default: runs at 06:00 UTC every day, reconciling the previous calendar day
 * (Stripe's settlement window typically closes before midnight UTC).
 *
 * <p>The job parameter {@code reportDate} is set to yesterday's date (ISO-8601 string).
 * Passing the date as a parameter ensures each run is a distinct {@link JobInstance},
 * so Spring Batch won't refuse to re-run a previously completed instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;

    @Value("${aegispay.reconciliation.cron:0 0 6 * * *}")
    private String cron; // unused by @Scheduled directly — here for documentation

    /**
     * Scheduled trigger. Spring reads the cron expression from the property at startup.
     * Set {@code aegispay.reconciliation.cron} in application.yml / env to override.
     */
    @Scheduled(cron = "${aegispay.reconciliation.cron:0 0 6 * * *}", zone = "UTC")
    public void runDailyReconciliation() {
        LocalDate reportDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        runFor(reportDate);
    }

    /**
     * Programmatic trigger — called from {@link com.aegispay.reconciliation.controller.ReconciliationController}
     * for manual / on-demand runs.
     *
     * @param reportDate the date to reconcile
     * @return the resulting {@link JobExecution}
     */
    public JobExecution runFor(LocalDate reportDate) {
        String dateStr = reportDate.toString();
        log.info("Launching reconciliation job for reportDate={}", dateStr);

        JobParameters params = new JobParametersBuilder()
                .addString("reportDate", dateStr)
                // Add current timestamp to allow re-running same date (e.g., after a fix)
                .addLong("launchedAt", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(reconciliationJob, params);
            log.info("Reconciliation job launched: executionId={} status={}",
                    execution.getId(), execution.getStatus());
            return execution;
        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("Reconciliation job already running for reportDate={}", dateStr);
            throw new IllegalStateException("Reconciliation job is already running for " + dateStr, e);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("Reconciliation job already completed for reportDate={} — use force flag to re-run", dateStr);
            throw new IllegalStateException("Reconciliation already completed for " + dateStr, e);
        } catch (JobRestartException | JobParametersInvalidException e) {
            log.error("Failed to launch reconciliation job for reportDate={}: {}", dateStr, e.getMessage(), e);
            throw new RuntimeException("Failed to launch reconciliation job", e);
        }
    }
}
