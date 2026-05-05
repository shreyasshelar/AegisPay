package com.aegispay.ai.audit;

import com.aegispay.ai.domain.entity.AiAuditLog;
import com.aegispay.ai.repository.AiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final AiAuditLogRepository repository;

    @Value("${aegispay.ai.audit.enabled:true}")
    private boolean enabled;

    public void log(String requestType, String maskedInput, String output,
                    String model, long latencyMs, String error) {
        if (!enabled) return;
        try {
            repository.save(AiAuditLog.builder()
                    .requestType(requestType)
                    .inputMasked(maskedInput)
                    .output(output)
                    .model(model)
                    .latencyMs(latencyMs)
                    .error(error)
                    .build());
        } catch (Exception e) {
            log.error("Failed to write AI audit log for requestType={}: {}", requestType, e.getMessage());
        }
    }
}
