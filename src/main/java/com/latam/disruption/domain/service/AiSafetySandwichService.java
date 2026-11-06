package com.latam.disruption.domain.service;

import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AnonymizedAiPromptContext;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.GuardedInquiryResolution;
import com.latam.disruption.domain.model.OptionViability;
import com.latam.disruption.domain.model.Passenger;
import com.latam.disruption.domain.model.TripContext;

import java.util.Locale;
import java.util.regex.Pattern;

public class AiSafetySandwichService {

    public static final double CALIBRATED_TEMPERATURE = 0.0;
    public static final double MINIMUM_CONFIDENCE_THRESHOLD = 0.85;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern DOCUMENT_PATTERN =
            Pattern.compile("\\b\\d{1,2}\\.?\\d{3}\\.?\\d{3}-[0-9Kk]\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\+?\\d[\\d\\s-]{7,15}\\d");
    private static final Pattern CASH_PROMISE_PATTERN =
            Pattern.compile("(?i)(transferencia aprobada|efectivo aprobado|reembolso inmediato aprobado)");

    public AiSafetySandwichService() {
        super();
    }

    public boolean detectsCashCompensationRequest(String inquiryText) {
        if (inquiryText == null || inquiryText.isBlank()) {
            return false;
        }
        String normalized = inquiryText.toLowerCase(Locale.ROOT);
        return normalized.contains("compensacion")
                || normalized.contains("compensación")
                || normalized.contains("indemnizacion")
                || normalized.contains("indemnización")
                || normalized.contains("efectivo")
                || normalized.contains("dinero");
    }

    public AnonymizedAiPromptContext buildSanitizedPromptContext(
            TripContext trip,
            DisruptionAssessment assessment,
            String rawQuestion
    ) {
        String sanitizedQuestion = redactPii(trip, rawQuestion);
        String recommendedOptionCode = assessment.recommendedOption() != null
                ? assessment.recommendedOption().optionId() + " ("
                + assessment.recommendedOption().flightNumber() + ")"
                : "NONE";

        String factsSummary = String.format(
                "Booking=%s | Passengers=%d | IncludesMinor=%s | MissedConnection=%s "
                        + "| DelayMinutes=%d | Gate=%s | PolicyVersion=%s | AppliedRules=%s "
                        + "| Entitlements=%s | ApprovedOption=%s | RequiresHumanApproval=%s",
                assessment.bookingId(),
                assessment.passengerCount(),
                assessment.includesMinor(),
                assessment.missedConnection(),
                assessment.primaryDelayMinutes(),
                assessment.updatedGate(),
                assessment.policyVersion(),
                String.join(",", assessment.appliedRuleIds()),
                String.join(",", assessment.entitlements()),
                recommendedOptionCode,
                assessment.requiresHumanApproval()
        );

        return new AnonymizedAiPromptContext(
                assessment.bookingId(),
                CALIBRATED_TEMPERATURE,
                MINIMUM_CONFIDENCE_THRESHOLD,
                assessment.policyVersion(),
                factsSummary,
                sanitizedQuestion
        );
    }

    public GuardedInquiryResolution validateAndGuardOutput(
            DisruptionAssessment assessment,
            AiDraftResponse aiDraft
    ) {
        if (aiDraft == null) {
            return buildFallback(assessment, "AI_ADAPTER_UNAVAILABLE_OR_NULL");
        }
        if (aiDraft.policyRef() == null
                || !assessment.policyVersion().equals(aiDraft.policyRef().trim())) {
            return buildFallback(assessment, "INVALID_OR_MISSING_POLICY_REF");
        }
        if (aiDraft.confidenceScore() < MINIMUM_CONFIDENCE_THRESHOLD) {
            return buildFallback(assessment, "LOW_CONFIDENCE_BELOW_THRESHOLD");
        }
        if (aiDraft.explanation() == null || aiDraft.explanation().isBlank()) {
            return buildFallback(assessment, "EMPTY_AI_EXPLANATION");
        }
        if (referencesRejectedOptionOrFlight(assessment, aiDraft)) {
            return buildFallback(assessment, "UNELIGIBLE_OPTION_OR_FLIGHT_REFERENCED");
        }
        if (CASH_PROMISE_PATTERN.matcher(aiDraft.explanation()).find()) {
            return buildFallback(assessment, "UNAUTHORIZED_CASH_COMPENSATION_PROMISE");
        }

        boolean finalHumanEscalation = assessment.requiresHumanApproval()
                || aiDraft.requiresHumanEscalation();

        return new GuardedInquiryResolution(
                true,
                "AI_ASSISTED_VALIDATED",
                assessment.policyVersion(),
                aiDraft.recommendedOptionId(),
                aiDraft.confidenceScore(),
                finalHumanEscalation,
                null,
                aiDraft.explanation().trim()
        );
    }

    private boolean referencesRejectedOptionOrFlight(
            DisruptionAssessment assessment,
            AiDraftResponse aiDraft
    ) {
        for (OptionViability viability : assessment.evaluatedOptions()) {
            if (!viability.eligible()) {
                String rejectedId = viability.option().optionId();
                String rejectedFlight = viability.option().flightNumber();
                if (rejectedId != null && rejectedId.equalsIgnoreCase(aiDraft.recommendedOptionId())) {
                    return true;
                }
                if (rejectedFlight != null && aiDraft.explanation().contains(rejectedFlight)) {
                    return true;
                }
            }
        }
        return false;
    }

    private GuardedInquiryResolution buildFallback(
            DisruptionAssessment assessment,
            String rejectionReason
    ) {
        String safeOptionId = assessment.recommendedOption() != null
                ? assessment.recommendedOption().optionId()
                : null;
        return new GuardedInquiryResolution(
                false,
                "DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED",
                assessment.policyVersion(),
                safeOptionId,
                0.0,
                true,
                rejectionReason,
                assessment.passengerExplanation()
        );
    }

    private String redactPii(TripContext trip, String rawText) {
        if (rawText == null) {
            return "";
        }
        String sanitized = EMAIL_PATTERN.matcher(rawText).replaceAll("[REDACTED_EMAIL]");
        sanitized = DOCUMENT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_DOCUMENT]");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
        if (trip != null && trip.passengers() != null) {
            for (Passenger passenger : trip.passengers()) {
                if (passenger.name() != null && !passenger.name().isBlank()) {
                    sanitized = sanitized.replace(passenger.name(), "[REDACTED_NAME]");
                }
                if (passenger.documentId() != null && !passenger.documentId().isBlank()) {
                    sanitized = sanitized.replace(passenger.documentId(), "[REDACTED_DOCUMENT]");
                }
                if (passenger.email() != null && !passenger.email().isBlank()) {
                    sanitized = sanitized.replace(passenger.email(), "[REDACTED_EMAIL]");
                }
                if (passenger.phone() != null && !passenger.phone().isBlank()) {
                    sanitized = sanitized.replace(passenger.phone(), "[REDACTED_PHONE]");
                }
            }
        }
        return sanitized;
    }
}
