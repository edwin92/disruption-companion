package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuditLogRecord(
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
}
