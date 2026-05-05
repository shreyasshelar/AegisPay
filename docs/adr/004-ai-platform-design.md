# ADR-004: Centralised AI Platform with RAG + Agentic Components

**Status**: Accepted
**Date**: 2026-05-02
**Deciders**: Platform Engineering, AI Team

---

## Context

AegisPay requires AI capabilities across multiple domains:
- Fraud explanation (augmenting rule-based risk decisions)
- Bank error resolution (translating opaque error codes to user-friendly guidance)
- Incident triage (SRE assistance)
- Document OCR + KYC (identity verification)

Each consuming service (risk-engine, user-service, transaction-service) should not manage its own LLM integration, model selection, API key rotation, or audit trail.

---

## Decision

Build a **centralised `ai-platform` service** that encapsulates all LLM interactions.

### Architecture

1. **RAG Pipeline** (`RagPipelineService`): Every AI query follows the same pipeline: similarity search on pgvector → context assembly → LLM completion → audit log entry. Top-K and similarity threshold are configurable per environment.

2. **Knowledge Bases**: Domain knowledge is ingested at startup via `@EventListener(ApplicationReadyEvent)`. Each knowledge base is a Spring bean that calls `ragPipeline.ingest()`. Adding new knowledge = adding a new `@Component` class.

3. **Fraud Copilot** (RAG): Augments rule-based risk scores with natural-language explanations grounded in historical fraud cases.

4. **Error Resolution** (RAG): Maps bank error codes to user-friendly explanations and recommended actions.

5. **Incident Triage Agent** (Agentic): Uses Spring AI `ChatClient` with registered `@Tool` methods (log reader, metrics query, deployment history). The LLM decides which tools to call and in what order — no hardcoded orchestration.

6. **OCR + KYC** (Multimodal): Passes base64-encoded document images to a vision-capable model via Spring AI `Media` API. Three sequential steps: quality scoring → tampering detection → data extraction.

### AI Audit Trail

Every LLM call is logged to `ai_audit_log` in a `finally` block with: masked input, raw output, model ID, and latency. This satisfies regulatory audit requirements for AI-assisted decisions in financial services.

### Model Selection

| Component | Model | Reason |
|---|---|---|
| RAG / Fraud / Error | `claude-sonnet-4-6` | High reasoning quality, long context |
| Incident Triage | `claude-sonnet-4-6` | Tool-use capability required |
| OCR / KYC | `claude-sonnet-4-6` | Multimodal (vision) capability |
| Embeddings | `text-embedding-3-small` | Cost-effective, sufficient for fintech KB |

Spring AI's abstraction layer (`ChatModel`, `EmbeddingModel`, `VectorStore`) allows swapping models without changing service code.

---

## Alternatives Considered

**Per-service LLM integration**: Each service manages its own API key, retry logic, and audit trail. Rejected because it creates N copies of the same boilerplate and makes model upgrades a multi-service change.

**LangChain4j instead of Spring AI**: More mature at the time of writing but couples us to a non-Spring lifecycle. Spring AI's first-class Spring Boot integration and VectorStore abstraction outweigh maturity differences for our stack.

---

## Consequences

- `ai-platform` becomes a dependency for risk-engine and user-service. Its availability directly affects payment processing (risk explanation) and KYC. It must have health checks and circuit breakers at the caller side.
- AI decisions are **advisory only** — the risk-engine's rule-based score drives the actual APPROVED/REJECTED/REVIEW decision. The RAG explanation augments but does not override it.
- LLM API keys (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) are stored in the secret store (Vault/AWS Secrets Manager) and injected via External Secrets Operator. They must never appear in ConfigMaps or logs.
- pgvector HNSW index parameters (`m=16`, `ef_construction=64`) are chosen for the current KB size (~100 documents). Larger KBs (>10k documents) require re-indexing with higher `m`.
