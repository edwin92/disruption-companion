package com.latam.disruption.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disruption_audit_logs")
public class DisruptionAuditLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 16)
    private String bookingId;

    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @Column(name = "resolution_mode", nullable = false, length = 64)
    private String resolutionMode;

    @Column(name = "policy_ref", nullable = false, length = 32)
    private String policyRef;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "human_approval_required", nullable = false)
    private boolean humanApprovalRequired;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DisruptionAuditLogJpaEntity() {
        super();
    }

    public DisruptionAuditLogJpaEntity(
            UUID id,
            String bookingId,
            String operationType,
            String resolutionMode,
            String policyRef,
            Double aiConfidence,
            boolean humanApprovalRequired,
            String summary,
            Instant createdAt
    ) {
        this.id = id;
        this.bookingId = bookingId;
        this.operationType = operationType;
        this.resolutionMode = resolutionMode;
        this.policyRef = policyRef;
        this.aiConfidence = aiConfidence;
        this.humanApprovalRequired = humanApprovalRequired;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getResolutionMode() {
        return resolutionMode;
    }

    public String getPolicyRef() {
        return policyRef;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public boolean isHumanApprovalRequired() {
        return humanApprovalRequired;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
