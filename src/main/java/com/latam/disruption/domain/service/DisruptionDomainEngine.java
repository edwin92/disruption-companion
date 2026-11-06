package com.latam.disruption.domain.service;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.FlightSegment;
import com.latam.disruption.domain.model.OperationalEvent;
import com.latam.disruption.domain.model.OperationalEventsSnapshot;
import com.latam.disruption.domain.model.OptionViability;
import com.latam.disruption.domain.model.PolicyRule;
import com.latam.disruption.domain.model.PolicyRulesSnapshot;
import com.latam.disruption.domain.model.TripContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DisruptionDomainEngine {

    private static final int RELEVANT_DELAY_MINUTES = 30;
    private static final int MINIMUM_CONNECTION_MINUTES = 60;

    public DisruptionDomainEngine() {
        super();
    }

    public DisruptionAssessment assess(
            TripContext trip,
            OperationalEventsSnapshot eventsSnapshot,
            PolicyRulesSnapshot policies,
            EligibleOptionsSnapshot optionsSnapshot,
            boolean compensationRequested,
            Instant evaluationClock
    ) {
        Set<String> degradedSources = eventsSnapshot.sourcesDegraded() == null
                ? Set.of()
                : Set.copyOf(eventsSnapshot.sourcesDegraded());

        List<OperationalEvent> trustedEvents = eventsSnapshot.events().stream()
                .filter(e -> !degradedSources.contains(e.source()))
                .toList();

        if (trustedEvents.isEmpty()) {
            throw new BusinessException(
                    TipoErrorNegocio.DEPENDENCIA_INDETERMINADA,
                    "ALL_OPERATIONAL_SOURCES_DEGRADED",
                    "No existen fuentes operacionales saludables para verificar el estado del vuelo"
            );
        }

        int primaryDelay = trustedEvents.stream()
                .filter(e -> "DELAY".equalsIgnoreCase(e.type()))
                .mapToInt(OperationalEvent::delayMinutes)
                .max()
                .orElse(0);

        String updatedDeparture = trustedEvents.stream()
                .filter(e -> "DELAY".equalsIgnoreCase(e.type()) && e.newDeparture() != null)
                .map(OperationalEvent::newDeparture)
                .findFirst()
                .orElse(null);

        String updatedGate = trustedEvents.stream()
                .filter(e -> "GATE_CHANGE".equalsIgnoreCase(e.type()) && e.gate() != null)
                .map(OperationalEvent::gate)
                .findFirst()
                .orElse(null);

        boolean hasTrustedOnTimeForFirstSegment = trustedEvents.stream()
                .anyMatch(e -> "S1".equals(e.segmentId())
                        && "STATUS".equalsIgnoreCase(e.type())
                        && "ON_TIME".equalsIgnoreCase(e.status()));

        boolean sourceConflict = primaryDelay > 0 && hasTrustedOnTimeForFirstSegment;
        boolean missedConnection = calculateMissedConnection(trip.segments(), primaryDelay);
        boolean relevantModification = primaryDelay >= RELEVANT_DELAY_MINUTES
                || missedConnection
                || updatedGate != null;

        boolean internationalRoute = isInternationalRoute(trip.segments());
        List<String> appliedRuleIds = new ArrayList<>();
        Set<String> entitlements = new LinkedHashSet<>();
        boolean requiresHumanApproval = sourceConflict;

        for (PolicyRule rule : policies.rules()) {
            if (matchesRule(rule, primaryDelay, internationalRoute, missedConnection,
                    compensationRequested, trip.includesMinor())) {
                appliedRuleIds.add(rule.ruleId());
                entitlements.add(rule.entitlement());
                if (rule.requiresHumanApproval()) {
                    requiresHumanApproval = true;
                }
            }
        }

        boolean entitledToNoFeeRebook = entitlements.contains("REBOOK_NO_FEE");
        boolean missingPenaltyField = trip.missingFields() != null
                && trip.missingFields().contains("fare_conditions.change_penalty_amount");
        boolean blockedByMissingPenalty = missingPenaltyField && !entitledToNoFeeRebook;

        boolean optionsExpired = optionsSnapshot != null
                && optionsSnapshot.isExpiredAt(evaluationClock);
        List<OptionViability> evaluatedOptions = evaluateCandidateOptions(
                trip, policies.version(), entitledToNoFeeRebook, optionsSnapshot, optionsExpired);

        CandidateOption recommended = evaluatedOptions.stream()
                .filter(OptionViability::eligible)
                .map(OptionViability::option)
                .findFirst()
                .orElse(null);

        boolean overnightRequired = isOvernightOption(trip.segments(), recommended);
        if (overnightRequired && trip.includesMinor()) {
            requiresHumanApproval = true;
        }

        String explanation = buildDeterministicExplanation(
                trip, primaryDelay, updatedDeparture, updatedGate, missedConnection,
                policies.version(), appliedRuleIds, recommended, requiresHumanApproval);

        return new DisruptionAssessment(
                trip.bookingId(),
                trip.passengerCount(),
                trip.includesMinor(),
                relevantModification,
                missedConnection,
                primaryDelay,
                updatedDeparture,
                updatedGate,
                sourceConflict,
                degradedSources,
                policies.version(),
                appliedRuleIds,
                entitlements,
                requiresHumanApproval,
                overnightRequired,
                blockedByMissingPenalty,
                optionsExpired,
                recommended,
                evaluatedOptions,
                explanation
        );
    }

    private boolean calculateMissedConnection(List<FlightSegment> segments, int firstSegmentDelayMinutes) {
        if (segments == null || segments.size() < 2 || firstSegmentDelayMinutes <= 0) {
            return false;
        }
        FlightSegment first = segments.get(0);
        FlightSegment second = segments.get(1);
        LocalDateTime firstArrival = LocalDateTime.of(
                LocalDate.parse(first.date()),
                LocalTime.parse(first.scheduledArrival())
        ).plusMinutes(firstSegmentDelayMinutes);

        LocalDateTime secondDeparture = LocalDateTime.of(
                LocalDate.parse(second.date()),
                LocalTime.parse(second.scheduledDeparture())
        );

        long connectionMinutes = ChronoUnit.MINUTES.between(firstArrival, secondDeparture);
        return connectionMinutes < MINIMUM_CONNECTION_MINUTES;
    }

    private boolean isInternationalRoute(List<FlightSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        Set<String> airports = new LinkedHashSet<>();
        for (FlightSegment seg : segments) {
            airports.add(seg.origin());
            airports.add(seg.destination());
        }
        return airports.size() > 1;
    }

    private boolean matchesRule(
            PolicyRule rule,
            int delayMinutes,
            boolean international,
            boolean missedConnection,
            boolean compensationRequested,
            boolean includesMinor
    ) {
        return switch (rule.ruleId()) {
            case "R1" -> delayMinutes >= 180;
            case "R2" -> delayMinutes >= 240 && international;
            case "R3" -> missedConnection;
            case "R4" -> compensationRequested;
            case "R5" -> includesMinor;
            default -> false;
        };
    }

    private List<OptionViability> evaluateCandidateOptions(
            TripContext trip,
            String activePolicyVersion,
            boolean entitledToNoFeeRebook,
            EligibleOptionsSnapshot snapshot,
            boolean optionsExpired
    ) {
        if (snapshot == null || snapshot.options() == null) {
            return List.of();
        }
        List<OptionViability> results = new ArrayList<>();
        for (CandidateOption option : snapshot.options()) {
            List<String> reasons = new ArrayList<>();
            if (optionsExpired) {
                reasons.add("OPTION_TTL_EXPIRED");
            }
            if ("REBOOK".equalsIgnoreCase(option.type())) {
                if (option.seatsAvailable() < trip.passengerCount()) {
                    reasons.add("INSUFFICIENT_SEATS_FOR_GROUP");
                }
                if (entitledToNoFeeRebook && option.fareDifference() != null
                        && option.fareDifference() > 0) {
                    reasons.add("NON_ZERO_FARE_WHEN_NO_FEE_ENTITLED");
                }
                if (!activePolicyVersion.equals(option.policyRef())) {
                    reasons.add("POLICY_VERSION_MISMATCH");
                }
            } else if ("REFUND".equalsIgnoreCase(option.type())) {
                if (!trip.fareConditions().refundable()
                        || "LOW".equalsIgnoreCase(option.confidence())) {
                    reasons.add("NON_REFUNDABLE_FARE_AND_LOW_CONFIDENCE");
                }
            }
            results.add(new OptionViability(option, reasons.isEmpty(), reasons));
        }
        return results;
    }

    private boolean isOvernightOption(List<FlightSegment> segments, CandidateOption recommended) {
        if (segments == null || segments.isEmpty() || recommended == null || recommended.date() == null) {
            return false;
        }
        String lastOriginalDate = segments.get(segments.size() - 1).date();
        return !lastOriginalDate.equals(recommended.date());
    }

    private String buildDeterministicExplanation(
            TripContext trip,
            int delayMinutes,
            String updatedDeparture,
            String updatedGate,
            boolean missedConnection,
            String policyVersion,
            List<String> appliedRuleIds,
            CandidateOption recommended,
            boolean requiresHumanApproval
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reserva ").append(trip.bookingId()).append(": ");
        if (delayMinutes > 0) {
            sb.append("El vuelo inicial registra una demora verificada de ")
                    .append(delayMinutes).append(" minutos");
            if (updatedDeparture != null) {
                sb.append(" (nueva salida estimada ").append(updatedDeparture).append(")");
            }
            if (updatedGate != null) {
                sb.append(" en puerta ").append(updatedGate);
            }
            sb.append(". ");
        }
        if (missedConnection) {
            sb.append("Debido a esta demora se pierde la conexion programada en Lima. ");
        }
        sb.append("Bajo la politica vigente ").append(policyVersion)
                .append(" (reglas aplicadas: ").append(String.join(", ", appliedRuleIds)).append(")");
        if (recommended != null) {
            sb.append(", corresponde cambio sin costo a la opcion ")
                    .append(recommended.optionId()).append(" (vuelo ")
                    .append(recommended.flightNumber()).append(" del ")
                    .append(recommended.date()).append(" a las ")
                    .append(recommended.departure()).append("). ");
        } else {
            sb.append(", no existen opciones automaticas con cupo vigente para el grupo. ");
        }
        if (requiresHumanApproval) {
            sb.append("El caso requiere asistencia prioritaria de un especialista humano.");
        }
        return sb.toString().trim();
    }
}
