package com.latam.disruption.domain.model;

public record GuardedInquiryResolution(
        boolean aiOutputAccepted,
        String resolutionMode,
        String validatedPolicyRef,
        String recommendedOptionId,
        double confidenceScore,
        boolean requiresHumanEscalation,
        String guardrailRejectionReason,
        String finalPassengerMessage
) {
}
