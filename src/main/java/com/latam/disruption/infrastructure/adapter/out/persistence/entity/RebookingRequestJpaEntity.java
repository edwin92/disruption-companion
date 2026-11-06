package com.latam.disruption.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rebooking_requests")
public class RebookingRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, length = 16)
    private String bookingId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "option_id", nullable = false, length = 16)
    private String optionId;

    @Column(name = "flight_number", nullable = false, length = 16)
    private String flightNumber;

    @Column(name = "policy_ref", nullable = false, length = 32)
    private String policyRef;

    @Column(name = "upstream_request_id", length = 80)
    private String upstreamRequestId;

    @Column(name = "status", nullable = false, length = 48)
    private String status;

    @Column(name = "notification_id", length = 32)
    private String notificationId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RebookingRequestJpaEntity() {
        super();
    }

    public RebookingRequestJpaEntity(
            UUID id,
            String bookingId,
            String idempotencyKey,
            String optionId,
            String flightNumber,
            String policyRef,
            String upstreamRequestId,
            String status,
            String notificationId,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.bookingId = bookingId;
        this.idempotencyKey = idempotencyKey;
        this.optionId = optionId;
        this.flightNumber = flightNumber;
        this.policyRef = policyRef;
        this.upstreamRequestId = upstreamRequestId;
        this.status = status;
        this.notificationId = notificationId;
        this.expiresAt = expiresAt;
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

    public String getOptionId() {
        return optionId;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getPolicyRef() {
        return policyRef;
    }

    public String getUpstreamRequestId() {
        return upstreamRequestId;
    }

    public String getStatus() {
        return status;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
