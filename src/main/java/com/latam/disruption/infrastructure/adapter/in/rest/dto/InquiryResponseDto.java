package com.latam.disruption.infrastructure.adapter.in.rest.dto;

public record InquiryResponseDto(
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
