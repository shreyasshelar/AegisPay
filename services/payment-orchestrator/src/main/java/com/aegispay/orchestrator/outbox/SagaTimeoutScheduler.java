package com.aegispay.orchestrator.outbox;

import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.aegispay.orchestrator.saga.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTimeoutScheduler {

    private final SagaRepository sagaRepository;
    private final PaymentSagaOrchestrator orchestrator;

    @Scheduled(fixedDelayString = "${aegispay.saga.timeout-check-interval-ms:60000}")
    public void checkTimeouts() {
        List<Saga> timedOut = sagaRepository.findTimedOutSagas(Instant.now());
        if (timedOut.isEmpty()) return;

        log.warn("Found {} timed-out saga(s)", timedOut.size());
        for (Saga saga : timedOut) {
            try {
                orchestrator.timeoutSaga(saga);
            } catch (Exception e) {
                log.error("Failed to timeout saga={}: {}", saga.getId(), e.getMessage(), e);
            }
        }
    }
}
