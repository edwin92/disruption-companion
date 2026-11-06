package com.latam.disruption.infrastructure.adapter.in.rest.dto;

public record EscalationResponseDto(
        String caseId,
        String bookingId,
        String queue,
        int slaMinutes,
        String reason,
        boolean replayedIdempotent
) {
}
