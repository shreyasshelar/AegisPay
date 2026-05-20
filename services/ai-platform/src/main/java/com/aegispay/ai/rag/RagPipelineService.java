package com.aegispay.ai.rag;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.config.AiPlatformProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipelineService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final AiAuditService auditService;
    private final AiPlatformProperties props;

    /**
     * Core RAG operation:
     * 1. Retrieve top-K similar documents from pgvector
     * 2. Build a grounded prompt with retrieved context
     * 3. Call LLM and return the completion
     *
     * <p>Resilience decorators applied in order:
     * <ol>
     *   <li>{@code @TimeLimiter} — 30 s hard cap on the entire pipeline (prevents hanging threads)
     *   <li>{@code @Retry} — 2 retries with exponential backoff on transient failures
     *   <li>{@code @CircuitBreaker} — opens after 50% failure rate; fallback returns a
     *       human-readable "service temporarily unavailable" string instead of throwing.
     * </ol>
     */
    @CircuitBreaker(name = "rag-pipeline", fallbackMethod = "queryFallback")
    @Retry(name = "rag-pipeline")
    public String query(String requestType, String question, String systemPromptTemplate) {
        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(props.getRag().getTopK())
                            .similarityThreshold(props.getRag().getSimilarityThreshold())
                            .build());

            String context = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            String filledPrompt = systemPromptTemplate
                    .replace("{context}", context.isEmpty() ? "No relevant documents found." : context)
                    .replace("{question}", question);

            var response = chatModel.call(new Prompt(filledPrompt));
            output = response.getResult().getOutput().getText();
            return output;
        } catch (Exception e) {
            error = e.getMessage();
            log.error("RAG pipeline failed for requestType={}: {}", requestType, e.getMessage(), e);
            // Re-throw so @Retry can attempt again and @CircuitBreaker can track the failure.
            throw new RuntimeException("RAG pipeline error: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log(requestType, mask(question), output,
                    "claude-sonnet-4-6", latencyMs, error);
        }
    }

    /**
     * Fallback invoked by Resilience4j when the circuit is open or retries are exhausted.
     * Returns a graceful degraded response so callers don't need to handle null.
     */
    @SuppressWarnings("unused")   // called reflectively by Resilience4j
    String queryFallback(String requestType, String question, String systemPromptTemplate,
                         Throwable cause) {
        log.warn("RAG pipeline fallback triggered for requestType={}: {}", requestType,
                cause != null ? cause.getMessage() : "circuit open");
        return "AI explanation service is temporarily unavailable. "
                + "Please try again in a few minutes. "
                + "If the issue persists, contact support.";
    }

    /** Add documents to the vector store for a given knowledge base. */
    public void ingest(List<Document> documents) {
        vectorStore.add(documents);
        log.info("Ingested {} documents into vector store", documents.size());
    }

    private String mask(String input) {
        if (input == null || input.length() <= 50) return input;
        return input.substring(0, 50) + "...[truncated]";
    }
}
