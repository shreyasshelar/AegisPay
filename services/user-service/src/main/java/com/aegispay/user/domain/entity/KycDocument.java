package com.aegispay.user.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** AADHAAR | PAN | PASSPORT | DRIVING_LICENSE */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** S3 / GCS object key pointing to the uploaded document. */
    @Column(name = "document_ref", nullable = false, length = 500)
    private String documentRef;

    /** PENDING → PROCESSING → COMPLETED / FAILED */
    @Column(name = "ocr_status", nullable = false, length = 30)
    @Builder.Default
    private String ocrStatus = "PENDING";

    /** Structured data extracted by AI OCR (stored as JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private Map<String, Object> extractedData;

    @Column(name = "tampered_flag", nullable = false)
    @Builder.Default
    private boolean tamperedFlag = false;

    /** AI quality score 0–100. */
    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
