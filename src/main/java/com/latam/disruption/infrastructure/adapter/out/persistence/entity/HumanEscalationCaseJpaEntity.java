package com.latam.disruption.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "human_cases")
public class HumanEscalationCaseJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 16)
    private String bookingId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "case_id", nullable = false, length = 32)
    private String caseId;

    @Column(name = "queue_name", nullable = false, length = 32)
    private String queueName;

    @Column(name = "sla_minutes", nullable = false)
    private int slaMinutes;

    @Column(name = "reason", nullable = false, length = 320)
    private String reason;

    @Column(name = "sanitized_evidence", nullable = false, columnDefinition = "TEXT")
    private String sanitizedEvidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public HumanEscalationCaseJpaEntity() {
        super();
    }

    public HumanEscalationCaseJpaEntity(
            UUID id,
            String bookingId,
            String idempotencyKey,
            String caseId,
            String queueName,
            int slaMinutes,
            String reason,
            String sanitizedEvidence,
            Instant createdAt
    ) {
        this.id = id;
        this.bookingId = bookingId;
        this.idempotencyKey = idempotencyKey;
        this.caseId = caseId;
        this.queueName = queueName;
        this.slaMinutes = slaMinutes;
        this.reason = reason;
        this.sanitizedEvidence = sanitizedEvidence;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getQueueName() {
        return queueName;
    }

    public int getSlaMinutes() {
        return slaMinutes;
    }

    public String getReason() {
        return reason;
    }

    public String getSanitizedEvidence() {
        return sanitizedEvidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
