package com.latam.disruption.application.dto;

public record EscalationResult(
        String caseId,
        String bookingId,
        String queue,
        int slaMinutes,
        String reason,
        boolean replayedIdempotent
) {
}
