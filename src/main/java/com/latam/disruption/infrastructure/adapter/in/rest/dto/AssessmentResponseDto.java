package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import java.util.List;
import java.util.Set;

public record AssessmentResponseDto(
        String bookingId,
        int passengerCount,
        boolean includesMinor,
        boolean relevantModification,
        boolean missedConnection,
        int primaryDelayMinutes,
        String updatedDepartureTime,
        String updatedGate,
        boolean sourceConflictDetected,
        Set<String> discardedSources,
        String policyVersion,
        List<String> appliedRuleIds,
        Set<String> entitlements,
        boolean requiresHumanApproval,
        boolean overnightRequired,
        boolean blockedByMissingPenaltyAmount,
        boolean optionsExpired,
        String recommendedOptionId,
        String recommendedFlightNumber,
        List<OptionEvaluationDto> evaluatedOptions,
        String passengerExplanation
) {
}
