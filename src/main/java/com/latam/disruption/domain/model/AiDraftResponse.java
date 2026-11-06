package com.latam.disruption.domain.model;

public record AiDraftResponse(
        String explanation,
        String policyRef,
        String recommendedOptionId,
        double confidenceScore,
        boolean requiresHumanEscalation
) {
}
