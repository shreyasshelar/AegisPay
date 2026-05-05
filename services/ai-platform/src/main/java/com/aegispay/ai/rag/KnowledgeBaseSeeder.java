package com.aegispay.ai.rag;

import com.aegispay.ai.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ingests seed knowledge-base documents into pgvector on startup if the table is empty.
 *
 * <p>Three knowledge bases are loaded:
 * <ul>
 *   <li>{@code knowledge/fraud_cases.json} — Historical fraud patterns for the Fraud Copilot RAG
 *   <li>{@code knowledge/bank_error_codes.json} — Error codes and resolutions for Error Resolution Agent
 *   <li>{@code knowledge/incident_logs.json} — Operational incidents for Incident Triage Agent
 * </ul>
 *
 * <p>The check is {@code COUNT > 0} on the knowledge_documents table so re-deployment
 * does not re-ingest. To force a re-seed, truncate the table and pgvector store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseSeeder implements ApplicationRunner {

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        long count = repository.count();
        if (count > 0) {
            log.info("Knowledge base already seeded with {} documents — skipping ingestion", count);
            return;
        }
        log.info("Knowledge base empty — starting seed ingestion...");
        long start = System.currentTimeMillis();

        List<Document> documents = new ArrayList<>();
        documents.addAll(loadFraudCases());
        documents.addAll(loadBankErrorCodes());
        documents.addAll(loadIncidentLogs());

        if (documents.isEmpty()) {
            log.warn("No seed documents found — check classpath:knowledge/*.json");
            return;
        }

        vectorStore.add(documents);
        long elapsed = System.currentTimeMillis() - start;
        log.info("Knowledge base seeded: {} documents embedded and stored in pgvector in {}ms",
                documents.size(), elapsed);
    }

    // ── Loaders ────────────────────────────────────────────────────────────────

    private List<Document> loadFraudCases() {
        try {
            List<Map<String, Object>> cases = readJson("knowledge/fraud_cases.json");
            return cases.stream().map(fc -> {
                String content = buildFraudCaseContent(fc);
                Map<String, Object> metadata = Map.of(
                        "source", "fraud_cases",
                        "id", fc.getOrDefault("id", ""),
                        "outcome", fc.getOrDefault("outcome", ""),
                        "riskScore", fc.getOrDefault("riskScore", 0),
                        "type", "fraud_case"
                );
                return new Document(content, metadata);
            }).toList();
        } catch (Exception e) {
            log.error("Failed to load fraud_cases.json: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Document> loadBankErrorCodes() {
        try {
            List<Map<String, Object>> codes = readJson("knowledge/bank_error_codes.json");
            return codes.stream().map(ec -> {
                String content = buildErrorCodeContent(ec);
                Map<String, Object> metadata = Map.of(
                        "source", "bank_error_codes",
                        "code", ec.getOrDefault("code", ""),
                        "category", ec.getOrDefault("category", ""),
                        "retryable", ec.getOrDefault("retryable", false),
                        "type", "error_code"
                );
                return new Document(content, metadata);
            }).toList();
        } catch (Exception e) {
            log.error("Failed to load bank_error_codes.json: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Document> loadIncidentLogs() {
        try {
            List<Map<String, Object>> incidents = readJson("knowledge/incident_logs.json");
            return incidents.stream().map(inc -> {
                String content = buildIncidentContent(inc);
                Map<String, Object> metadata = Map.of(
                        "source", "incident_logs",
                        "id", inc.getOrDefault("id", ""),
                        "severity", inc.getOrDefault("severity", ""),
                        "service", inc.getOrDefault("service", ""),
                        "type", "incident"
                );
                return new Document(content, metadata);
            }).toList();
        } catch (Exception e) {
            log.error("Failed to load incident_logs.json: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ── Content builders ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String buildFraudCaseContent(Map<String, Object> fc) {
        StringBuilder sb = new StringBuilder();
        sb.append("FRAUD CASE: ").append(fc.get("id")).append("\n");
        sb.append("Title: ").append(fc.get("title")).append("\n");
        sb.append("Description: ").append(fc.get("description")).append("\n");
        sb.append("Outcome: ").append(fc.get("outcome")).append("\n");
        sb.append("Risk Score: ").append(fc.get("riskScore")).append("\n");
        Object signals = fc.get("riskSignals");
        if (signals instanceof List<?> list) {
            sb.append("Risk Signals: ").append(String.join(", ", (List<String>) list)).append("\n");
        }
        sb.append("Resolution: ").append(fc.get("resolution")).append("\n");
        return sb.toString().trim();
    }

    private String buildErrorCodeContent(Map<String, Object> ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("BANK ERROR CODE: ").append(ec.get("code")).append("\n");
        sb.append("Title: ").append(ec.get("title")).append("\n");
        sb.append("Category: ").append(ec.get("category")).append("\n");
        sb.append("Description: ").append(ec.get("description")).append("\n");
        sb.append("Customer Message: ").append(ec.get("customerMessage")).append("\n");
        sb.append("Resolution: ").append(ec.get("resolution")).append("\n");
        sb.append("Retryable: ").append(ec.get("retryable")).append("\n");
        if (ec.containsKey("suggestedAction")) {
            sb.append("Suggested Action: ").append(ec.get("suggestedAction")).append("\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String buildIncidentContent(Map<String, Object> inc) {
        StringBuilder sb = new StringBuilder();
        sb.append("INCIDENT: ").append(inc.get("id")).append("\n");
        sb.append("Title: ").append(inc.get("title")).append("\n");
        sb.append("Severity: ").append(inc.get("severity")).append("\n");
        sb.append("Service: ").append(inc.get("service")).append("\n");
        sb.append("Root Cause: ").append(inc.get("rootCause")).append("\n");
        Object symptoms = inc.get("symptoms");
        if (symptoms instanceof List<?> list) {
            sb.append("Symptoms: ").append(String.join("; ", (List<String>) list)).append("\n");
        }
        Object resolution = inc.get("resolution");
        if (resolution instanceof List<?> list) {
            sb.append("Resolution Steps: ").append(String.join("; ", (List<String>) list)).append("\n");
        }
        Object prevention = inc.get("prevention");
        if (prevention instanceof List<?> list) {
            sb.append("Prevention: ").append(String.join("; ", (List<String>) list)).append("\n");
        }
        return sb.toString().trim();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> readJson(String classpathPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        return objectMapper.readValue(resource.getInputStream(),
                new TypeReference<>() {});
    }
}
