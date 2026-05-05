package com.aegispay.ai.fraud;

import com.aegispay.ai.rag.RagPipelineService;
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
public class FraudKnowledgeBase {

    private final RagPipelineService ragPipeline;

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        log.info("Ingesting fraud knowledge base into vector store...");
        List<Document> docs = List.of(
            new Document("High velocity fraud pattern: When a user submits more than 10 transactions " +
                "within 5 minutes, it typically indicates automated fraud or account takeover. " +
                "Legitimate users rarely initiate more than 3 transactions per minute.",
                Map.of("source", "fraud-kb", "category", "velocity")),

            new Document("Cross-border transaction fraud: Transactions directed to foreign countries, " +
                "especially high-risk jurisdictions, carry elevated fraud risk. " +
                "Most legitimate domestic users transact within their home country.",
                Map.of("source", "fraud-kb", "category", "geo")),

            new Document("Large amount threshold violation: Unverified accounts attempting transactions " +
                "above INR 10,000 have historically shown 3x higher chargeback rates. " +
                "Require KYC completion before allowing high-value transfers.",
                Map.of("source", "fraud-kb", "category", "amount")),

            new Document("Blacklisted entity pattern: Entities on the fraud blacklist have been " +
                "verified through prior fraud incidents, chargebacks, or regulatory flags. " +
                "Any transaction involving a blacklisted user, IP, or IBAN must be immediately rejected.",
                Map.of("source", "fraud-kb", "category", "blacklist")),

            new Document("Account takeover indicators: Sudden change in transaction amount, " +
                "geography, or device fingerprint following a login from an unusual location " +
                "are strong indicators of account takeover fraud.",
                Map.of("source", "fraud-kb", "category", "ato"))
        );
        ragPipeline.ingest(docs);
        log.info("Fraud knowledge base ingested: {} documents", docs.size());
    }
}
