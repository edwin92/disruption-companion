package com.latam.disruption.domain.model;

import java.time.Instant;

public record UpstreamRebookingReceipt(
        String requestId,
        String status,
        Instant expiresAt
) {
}
