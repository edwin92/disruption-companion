package com.latam.disruption.application.dto;

public record RebookingCommand(
        String bookingId,
        String idempotencyKey,
        String optionId
) {
}
