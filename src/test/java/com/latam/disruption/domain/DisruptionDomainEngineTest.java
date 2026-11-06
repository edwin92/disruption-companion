package com.latam.disruption.domain;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.FareConditions;
import com.latam.disruption.domain.model.FlightSegment;
import com.latam.disruption.domain.model.OperationalEvent;
import com.latam.disruption.domain.model.OperationalEventsSnapshot;
import com.latam.disruption.domain.model.OptionViability;
import com.latam.disruption.domain.model.Passenger;
import com.latam.disruption.domain.model.PolicyRule;
import com.latam.disruption.domain.model.PolicyRulesSnapshot;
import com.latam.disruption.domain.model.TripContext;
import com.latam.disruption.domain.service.DisruptionDomainEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptionDomainEngineTest {

    private DisruptionDomainEngine engine;
    private Instant evaluationClock;

    @BeforeEach
    void setUp() {
        engine = new DisruptionDomainEngine();
        evaluationClock = Instant.parse("2026-09-15T11:03:00Z");
    }

    @Test
    void casoBaseAbc123DetectaConexionPerdidaDescartaFuenteDegradadaYOpcionSinAsientos() {
        TripContext trip = crearTripAbc123();
        OperationalEventsSnapshot events = crearEventosAbc123();
        PolicyRulesSnapshot policies = crearPoliticasDelay();
        EligibleOptionsSnapshot options = crearOpcionesAbc123();

        DisruptionAssessment assessment = engine.assess(
                trip, events, policies, options, false, evaluationClock);

        assertEquals("ABC123", assessment.bookingId());
        assertTrue(assessment.relevantModification());
        assertTrue(assessment.missedConnection());
        assertEquals(160, assessment.primaryDelayMinutes());
        assertEquals("24", assessment.updatedGate());
        assertEquals("17:10", assessment.updatedDepartureTime());
        assertFalse(assessment.sourceConflictDetected());
        assertTrue(assessment.discardedSources().contains("LEGACY_MIRROR"));

        assertTrue(assessment.appliedRuleIds().contains("R3"));
        assertTrue(assessment.appliedRuleIds().contains("R5"));
        assertFalse(assessment.appliedRuleIds().contains("R1"));
        assertFalse(assessment.appliedRuleIds().contains("R2"));
        assertFalse(assessment.appliedRuleIds().contains("R4"));

        assertTrue(assessment.entitlements().contains("REBOOK_NO_FEE"));
        assertTrue(assessment.entitlements().contains("PRIORITY_HANDLING"));
        assertTrue(assessment.requiresHumanApproval());
        assertTrue(assessment.overnightRequired());
        assertFalse(assessment.blockedByMissingPenaltyAmount());

        assertNotNull(assessment.recommendedOption());
        assertEquals("O1", assessment.recommendedOption().optionId());
        assertEquals("LA2412", assessment.recommendedOption().flightNumber());

        OptionViability o2Evaluation = assessment.evaluatedOptions().stream()
                .filter(o -> "O2".equals(o.option().optionId()))
                .findFirst()
                .orElseThrow();
        assertFalse(o2Evaluation.eligible());
        assertTrue(o2Evaluation.rejectionReasons().contains("INSUFFICIENT_SEATS_FOR_GROUP"));
        assertTrue(o2Evaluation.rejectionReasons().contains("NON_ZERO_FARE_WHEN_NO_FEE_ENTITLED"));

        OptionViability o3Evaluation = assessment.evaluatedOptions().stream()
                .filter(o -> "O3".equals(o.option().optionId()))
                .findFirst()
                .orElseThrow();
        assertFalse(o3Evaluation.eligible());
    }

    @Test
    void atrasoMayorA240MinutosEnRutaInternacionalActivaR1YR2MealVoucher() {
        TripContext trip = crearTripAbc123();
        OperationalEventsSnapshot events = new OperationalEventsSnapshot(
                List.of(new OperationalEvent(
                        "E-1001", "S1", "DELAY", "18:45", 255, null, null,
                        "OPS_CORE", Instant.parse("2026-09-15T10:50:00Z"))),
                Instant.parse("2026-09-15T11:02:33Z"),
                Set.of("OPS_CORE"),
                Set.of()
        );

        DisruptionAssessment assessment = engine.assess(
                trip, events, crearPoliticasDelay(), crearOpcionesAbc123(), false, evaluationClock);

        assertTrue(assessment.appliedRuleIds().contains("R1"));
        assertTrue(assessment.appliedRuleIds().contains("R2"));
        assertTrue(assessment.appliedRuleIds().contains("R3"));
        assertTrue(assessment.entitlements().contains("MEAL_VOUCHER"));
    }

    @Test
    void fuentesSaludablesContradictoriasMarcanConflictoYExigenIntervencionHumana() {
        TripContext trip = crearTripAbc123();
        OperationalEventsSnapshot events = new OperationalEventsSnapshot(
                List.of(
                        new OperationalEvent(
                                "E-1001", "S1", "DELAY", "17:10", 160, null, null,
                                "OPS_CORE", Instant.parse("2026-09-15T10:55:00Z")),
                        new OperationalEvent(
                                "E-1002", "S1", "STATUS", null, 0, null, "ON_TIME",
                                "AIRPORT_FEED", Instant.parse("2026-09-15T10:56:00Z"))
                ),
                Instant.parse("2026-09-15T11:02:33Z"),
                Set.of("OPS_CORE", "AIRPORT_FEED"),
                Set.of()
        );

        DisruptionAssessment assessment = engine.assess(
                trip, events, crearPoliticasDelay(), crearOpcionesAbc123(), false, evaluationClock);

        assertTrue(assessment.sourceConflictDetected());
        assertTrue(assessment.requiresHumanApproval());
    }

    @Test
    void todasLasFuentesDegradadasLanzaExcepcionDeDependenciaIndeterminada() {
        TripContext trip = crearTripAbc123();
        OperationalEventsSnapshot events = new OperationalEventsSnapshot(
                List.of(new OperationalEvent(
                        "E-1012", "S1", "STATUS", null, 0, null, "ON_TIME",
                        "LEGACY_MIRROR", Instant.parse("2026-09-15T09:30:00Z"))),
                Instant.parse("2026-09-15T11:02:33Z"),
                Set.of("LEGACY_MIRROR"),
                Set.of("LEGACY_MIRROR")
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                engine.assess(trip, events, crearPoliticasDelay(), crearOpcionesAbc123(),
                        false, evaluationClock));
        assertEquals(TipoErrorNegocio.DEPENDENCIA_INDETERMINADA, ex.getTipo());
    }

    @Test
    void opcionesConTtlVencidoQuedanDescartadasPorExpiracion() {
        TripContext trip = crearTripAbc123();
        Instant expiredClock = Instant.parse("2026-09-15T11:10:00Z");

        DisruptionAssessment assessment = engine.assess(
                trip, crearEventosAbc123(), crearPoliticasDelay(), crearOpcionesAbc123(),
                false, expiredClock);

        assertTrue(assessment.optionsExpired());
        assertTrue(assessment.evaluatedOptions().stream().noneMatch(OptionViability::eligible));
    }

    static TripContext crearTripAbc123() {
        return new TripContext(
                "ABC123",
                List.of(
                        new Passenger("P1", "María Silva", "ADULT", null,
                                "maria.silva@example.com", "+56 9 8765 4321", "12.345.678-9", "GOLD"),
                        new Passenger("P2", "Tomás Silva", "CHILD", 7, null, null, null, null)
                ),
                List.of(
                        new FlightSegment("S1", "LA500", "SCL", "LIM", "2026-09-15", "14:30", "17:55"),
                        new FlightSegment("S2", "LA2410", "LIM", "MEX", "2026-09-15", "19:05", "23:40")
                ),
                new FareConditions(true, false, "LIGHT", null),
                Instant.parse("2026-09-15T11:02:31Z"),
                "PARTIAL",
                List.of("fare_conditions.change_penalty_amount")
        );
    }

    static OperationalEventsSnapshot crearEventosAbc123() {
        return new OperationalEventsSnapshot(
                List.of(
                        new OperationalEvent("E-1001", "S1", "DELAY", "17:10", 160, null, null,
                                "OPS_CORE", Instant.parse("2026-09-15T10:48:00Z")),
                        new OperationalEvent("E-1007", "S1", "GATE_CHANGE", null, 0, "24", null,
                                "AIRPORT_FEED", Instant.parse("2026-09-15T10:55:00Z")),
                        new OperationalEvent("E-1012", "S1", "STATUS", null, 0, null, "ON_TIME",
                                "LEGACY_MIRROR", Instant.parse("2026-09-15T09:30:00Z")),
                        new OperationalEvent("E-2003", "S2", "STATUS", null, 0, null, "SCHEDULED",
                                "OPS_CORE", Instant.parse("2026-09-15T10:48:00Z"))
                ),
                Instant.parse("2026-09-15T11:02:33Z"),
                Set.of("OPS_CORE", "AIRPORT_FEED", "LEGACY_MIRROR"),
                Set.of("LEGACY_MIRROR")
        );
    }

    static PolicyRulesSnapshot crearPoliticasDelay() {
        return new PolicyRulesSnapshot(
                "DELAY",
                "POL-DELAY-2026-03",
                "2026-03-01",
                List.of(
                        new PolicyRule("R1", "delay_minutes >= 180", "REBOOK_NO_FEE", false),
                        new PolicyRule("R2", "delay_minutes >= 240 AND international", "MEAL_VOUCHER", false),
                        new PolicyRule("R3", "missed_connection == true", "REBOOK_NO_FEE", false),
                        new PolicyRule("R4", "compensation_requested == true", "CASH_COMPENSATION", true),
                        new PolicyRule("R5", "group_includes_minor == true", "PRIORITY_HANDLING", true)
                ),
                Instant.parse("2026-09-15T11:02:36Z"),
                List.of("POL-DELAY-2025-11")
        );
    }

    static EligibleOptionsSnapshot crearOpcionesAbc123() {
        return new EligibleOptionsSnapshot(
                List.of(
                        new CandidateOption("O1", "REBOOK", "LA2412", "LIM", "MEX",
                                "2026-09-16", "08:15", 0L, "CLP", 4, "POL-DELAY-2026-03", "HIGH"),
                        new CandidateOption("O2", "REBOOK", "LA2450", "LIM", "MEX",
                                "2026-09-15", "23:40", 180000L, "CLP", 1, "POL-DELAY-2026-03", "MEDIUM"),
                        new CandidateOption("O3", "REFUND", null, null, null,
                                null, null, 0L, "CLP", 0, "POL-REFUND-2026-01", "LOW")
                ),
                Instant.parse("2026-09-15T11:02:35Z"),
                120
        );
    }
}
