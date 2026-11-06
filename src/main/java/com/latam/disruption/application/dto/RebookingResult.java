package com.latam.disruption.application.dto;

import java.time.Instant;

public record RebookingResult(
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
