package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import java.time.Instant;

public record RebookingResponseDto(
        String requestId,
        String bookingId,
        String optionId,
        String flightNumber,
        String status,
        String policyRef,
        String notificationId,
        Instant expiresAt,
        boolean replayedIdempotent
) {
}
