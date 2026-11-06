package com.latam.disruption.domain.model;

import java.util.List;
import java.util.Set;

public record DisruptionAssessment(
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
        CandidateOption recommendedOption,
        List<OptionViability> evaluatedOptions,
        String passengerExplanation
) {
}
