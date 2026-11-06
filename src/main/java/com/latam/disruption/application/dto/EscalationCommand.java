package com.latam.disruption.application.dto;

public record EscalationCommand(
        String bookingId,
        String idempotencyKey,
        String reason
) {
}
