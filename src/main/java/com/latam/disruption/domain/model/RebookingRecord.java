package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RebookingRecord(
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
}
