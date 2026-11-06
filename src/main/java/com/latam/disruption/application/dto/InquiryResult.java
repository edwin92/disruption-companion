package com.latam.disruption.application.dto;

public record InquiryResult(
        String bookingId,
        String resolutionMode,
        String validatedPolicyRef,
        String recommendedOptionId,
        double confidenceScore,
        boolean requiresHumanEscalation,
        String humanCaseId,
        String guardrailRejectionReason,
        String message
) {
}
