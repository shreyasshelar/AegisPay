package com.aegispay.ai.error;

import com.aegispay.ai.rag.RagPipelineService;
import com.aegispay.ai.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankErrorKnowledgeBase {

    private static final String SOURCE = "bank-errors";

    private final RagPipelineService ragPipeline;
    private final KnowledgeDocumentRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        if (!repository.findBySource(SOURCE).isEmpty()) {
            log.info("Bank error knowledge base already seeded — skipping ingestion");
            return;
        }
        log.info("Ingesting bank error knowledge base...");
        List<Document> docs = List.of(
            new Document("Error code INSUFFICIENT_FUNDS: The payer's account does not have enough " +
                "available balance to complete this transaction. " +
                "Action: Ask the user to top up their account or use a different payment method. " +
                "This is a permanent error until funds are added.",
                Map.of("source", "bank-errors", "code", "INSUFFICIENT_FUNDS")),

            new Document("Error code GATEWAY_UNAVAILABLE: The external payment gateway is temporarily " +
                "unreachable due to a network or infrastructure issue. " +
                "Action: Retry the transaction after 5 minutes. " +
                "This is a transient error that usually resolves automatically.",
                Map.of("source", "bank-errors", "code", "GATEWAY_UNAVAILABLE")),

            new Document("Error code RISK_REJECTED: The transaction was declined by the risk engine " +
                "due to suspicious activity patterns. " +
                "Action: Contact customer support to review the account. " +
                "If the user believes this is a mistake, they can submit an appeal. " +
                "This is a permanent error until the account is reviewed.",
                Map.of("source", "bank-errors", "code", "RISK_REJECTED")),

            new Document("Error code SAGA_TIMEOUT: The payment orchestration process timed out " +
                "because one of the downstream services did not respond within the expected window. " +
                "Action: Check if the payment was processed by contacting your bank before retrying " +
                "to avoid double charges. This is typically a transient infrastructure issue.",
                Map.of("source", "bank-errors", "code", "SAGA_TIMEOUT")),

            new Document("Error code INVALID_ACCOUNT: The destination account number or IBAN is " +
                "invalid or does not exist. " +
                "Action: Verify the payee account details and try again. " +
                "This is a permanent error requiring corrected account information.",
                Map.of("source", "bank-errors", "code", "INVALID_ACCOUNT"))
        );
        ragPipeline.ingest(docs);
        log.info("Bank error knowledge base ingested: {} documents", docs.size());
    }
}
