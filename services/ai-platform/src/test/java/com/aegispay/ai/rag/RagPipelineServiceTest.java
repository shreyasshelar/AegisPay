package com.aegispay.ai.rag;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.config.AiPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagPipelineServiceTest {

    private VectorStore vectorStore;
    private ChatModel chatModel;
    private AiAuditService auditService;
    private RagPipelineService ragPipeline;

    @BeforeEach
    void setup() {
        vectorStore = mock(VectorStore.class);
        chatModel = mock(ChatModel.class);
        auditService = mock(AiAuditService.class);

        AiPlatformProperties props = new AiPlatformProperties();
        ragPipeline = new RagPipelineService(vectorStore, chatModel, auditService, props);
    }

    @Test
    void query_retrieves_docs_and_returns_llm_output() {
        Document doc = new Document("Fraud case: velocity attack from shared IP");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        ChatResponse chatResponse = mockChatResponse("High velocity suggests coordinated fraud.");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        String result = ragPipeline.query("TEST", "why was this flagged?", "{context}\n{question}");

        assertThat(result).isEqualTo("High velocity suggests coordinated fraud.");
        verify(auditService).log(eq("TEST"), any(), eq(result), any(), anyLong(), isNull());
    }

    @Test
    void query_uses_fallback_message_when_no_docs_found() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatResponse chatResponse = mockChatResponse("No context available.");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        String result = ragPipeline.query("TEST", "question", "{context}\n{question}");
        assertThat(result).isNotNull();
    }

    @Test
    void query_logs_error_and_rethrows_on_llm_failure() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        assertThatThrownBy(() -> ragPipeline.query("TEST", "q", "{context}\n{question}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("RAG pipeline error");

        verify(auditService).log(eq("TEST"), any(), isNull(), any(), anyLong(), contains("LLM unavailable"));
    }

    @Test
    void ingest_adds_documents_to_vector_store() {
        List<Document> docs = List.of(new Document("doc1"), new Document("doc2"));
        ragPipeline.ingest(docs);
        verify(vectorStore).add(docs);
    }

    private ChatResponse mockChatResponse(String text) {
        AssistantMessage message = new AssistantMessage(text);
        Generation generation = new Generation(message);
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
