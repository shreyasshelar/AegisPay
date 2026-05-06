package com.aegispay.ai.integration;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.config.AiPlatformProperties;
import com.aegispay.ai.rag.RagPipelineService;
import com.aegispay.ai.repository.AiAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class AiPlatformIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("aegispay_ai_test")
            .withUsername("aegispay")
            .withPassword("aegispay");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        // Use jwk-set-uri (not issuer-uri) to skip OIDC discovery HTTP call at startup
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/realms/test/protocol/openid-connect/certs");
    }

    @Autowired AiAuditLogRepository auditLogRepository;
    @Autowired AiPlatformProperties props;

    @MockBean VectorStore vectorStore;
    @MockBean ChatModel chatModel;

    @Test
    void audit_log_is_written_after_rag_query() {
        AiAuditService auditService = new AiAuditService(auditLogRepository);
        RagPipelineService ragPipeline = new RagPipelineService(vectorStore, chatModel, auditService, props);

        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(new Document("fraud context")));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation generation = new Generation(new AssistantMessage("Fraud explanation result."));
        when(mockResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);

        String result = ragPipeline.query("FRAUD_EXPLAIN", "why flagged?", "{context}\n{question}");

        assertThat(result).isEqualTo("Fraud explanation result.");
        assertThat(auditLogRepository.count()).isGreaterThan(0);
    }
}
