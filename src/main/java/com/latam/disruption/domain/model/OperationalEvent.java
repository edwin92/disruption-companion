package com.latam.disruption.domain.model;

import java.time.Instant;

public record OperationalEvent(
        String eventId,
        String segmentId,
        String type,
        String newDeparture,
        int delayMinutes,
        String gate,
        String status,
        String source,
        Instant recordedAt
) {
}
