package com.aegispay.ai.rag;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.config.AiPlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
     */
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
            throw new RuntimeException("RAG pipeline error: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log(requestType, mask(question), output,
                    "claude-sonnet-4-6", latencyMs, error);
        }
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
