package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record OperationalEventsSnapshot(
        List<OperationalEvent> events,
        Instant retrievedAt,
        Set<String> sourcesAvailable,
        Set<String> sourcesDegraded
) {
}
