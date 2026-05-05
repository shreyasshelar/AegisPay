package com.aegispay.orchestrator.outbox;

import com.aegispay.common.kafka.OutboxRecord;
import com.aegispay.common.kafka.OutboxSchedulerBase;
import com.aegispay.orchestrator.repository.OutboxEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OutboxScheduler extends OutboxSchedulerBase {

    private final OutboxEntryRepository repository;

    public OutboxScheduler(KafkaTemplate<String, String> kafkaTemplate,
                           OutboxEntryRepository repository) {
        super(kafkaTemplate);
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${aegispay.outbox.poll-interval-ms:500}")
    @Transactional
    public void poll() {
        processOutbox();
    }

    @Override
    protected List<? extends OutboxRecord> fetchPendingEntries(int batchSize) {
        return repository.findPendingForUpdate(PageRequest.of(0, batchSize));
    }

    @Override
    protected void markPublished(UUID id) {
        repository.markPublished(id, Instant.now());
    }

    @Override
    protected void markFailed(UUID id, String errorMessage) {
        repository.markFailed(id, errorMessage);
    }
}
