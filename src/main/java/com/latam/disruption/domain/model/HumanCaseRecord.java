package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.UUID;

public record HumanCaseRecord(
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
}
