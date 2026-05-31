package com.aegispay.ai.error;

import com.aegispay.ai.rag.RagPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErrorResolutionServiceTest {

    private RagPipelineService ragPipeline;
    private ErrorResolutionService service;

    @BeforeEach
    void setup() {
        ragPipeline = mock(RagPipelineService.class);
        service = new ErrorResolutionService(ragPipeline);
    }

    @Test
    void resolve_returns_resolution_from_rag() {
        when(ragPipeline.query(eq("ERROR_RESOLVE"), anyString(), anyString()))
                .thenReturn("Ask the user to add funds.");

        ErrorResolutionService.ErrorResolutionResponse response =
                service.resolve("INSUFFICIENT_FUNDS", "Not enough balance");

        assertThat(response.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.resolution()).isEqualTo("Ask the user to add funds.");
    }

    @Test
    void resolve_includes_error_code_in_question() {
        when(ragPipeline.query(anyString(), anyString(), anyString())).thenReturn("ok");

        service.resolve("GATEWAY_UNAVAILABLE", "timeout");

        verify(ragPipeline).query(anyString(), contains("GATEWAY_UNAVAILABLE"), anyString());
    }
}
