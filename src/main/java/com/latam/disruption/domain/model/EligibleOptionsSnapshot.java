package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.List;

public record EligibleOptionsSnapshot(
        List<CandidateOption> options,
        Instant retrievedAt,
        int ttlSeconds
) {
    public boolean isExpiredAt(Instant clock) {
        if (retrievedAt == null || clock == null) {
            return false;
        }
        return clock.isAfter(retrievedAt.plusSeconds(ttlSeconds));
    }
}
