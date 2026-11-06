package com.latam.disruption.domain.model;

public record AnonymizedAiPromptContext(
        String bookingId,
        double temperature,
        double minimumConfidenceThreshold,
        String policyVersion,
        String systemFactsSummary,
        String sanitizedQuestion
) {
}
