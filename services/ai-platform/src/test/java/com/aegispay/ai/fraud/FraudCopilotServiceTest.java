package com.aegispay.ai.fraud;

import com.aegispay.ai.rag.RagPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudCopilotServiceTest {

    private RagPipelineService ragPipeline;
    private FraudCopilotService service;

    @BeforeEach
    void setup() {
        ragPipeline = mock(RagPipelineService.class);
        service = new FraudCopilotService(ragPipeline);
    }

    @Test
    void explain_delegates_to_rag_pipeline() {
        UUID txId = UUID.randomUUID();
        when(ragPipeline.query(eq("FRAUD_EXPLAIN"), anyString(), anyString()))
                .thenReturn("Velocity spike detected from shared IP.");

        String result = service.explain(txId, 80, List.of("VELOCITY", "BLACKLIST"));

        assertThat(result).isEqualTo("Velocity spike detected from shared IP.");
        verify(ragPipeline).query(eq("FRAUD_EXPLAIN"), contains("80"), anyString());
    }

    @Test
    void explain_includes_flagged_rules_in_question() {
        UUID txId = UUID.randomUUID();
        when(ragPipeline.query(anyString(), anyString(), anyString())).thenReturn("ok");

        service.explain(txId, 55, List.of("GEO_ANOMALY", "NEW_DEVICE"));

        verify(ragPipeline).query(anyString(), contains("GEO_ANOMALY"), anyString());
    }
}
