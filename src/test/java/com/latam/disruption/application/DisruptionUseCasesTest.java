package com.latam.disruption.application;

import com.latam.disruption.application.dto.EscalationCommand;
import com.latam.disruption.application.dto.EscalationResult;
import com.latam.disruption.application.dto.InquiryCommand;
import com.latam.disruption.application.dto.InquiryResult;
import com.latam.disruption.application.dto.RebookingCommand;
import com.latam.disruption.application.dto.RebookingResult;
import com.latam.disruption.application.port.out.AiInferencePort;
import com.latam.disruption.application.port.out.DisruptionPersistencePort;
import com.latam.disruption.application.port.out.EligibleOptionsPort;
import com.latam.disruption.application.port.out.HumanCaseGatewayPort;
import com.latam.disruption.application.port.out.OperationalEventsPort;
import com.latam.disruption.application.port.out.PassengerNotificationPort;
import com.latam.disruption.application.port.out.PolicyRulesPort;
import com.latam.disruption.application.port.out.RebookingGatewayPort;
import com.latam.disruption.application.port.out.TripContextPort;
import com.latam.disruption.application.usecase.DisruptionOrchestratorUseCase;
import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AuditLogRecord;
import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.FareConditions;
import com.latam.disruption.domain.model.FlightSegment;
import com.latam.disruption.domain.model.HumanCaseRecord;
import com.latam.disruption.domain.model.IdempotencyKey;
import com.latam.disruption.domain.model.OperationalEvent;
import com.latam.disruption.domain.model.OperationalEventsSnapshot;
import com.latam.disruption.domain.model.Passenger;
import com.latam.disruption.domain.model.PassengerNotificationReceipt;
import com.latam.disruption.domain.model.PolicyRule;
import com.latam.disruption.domain.model.PolicyRulesSnapshot;
import com.latam.disruption.domain.model.RebookingRecord;
import com.latam.disruption.domain.model.TripContext;
import com.latam.disruption.domain.model.UpstreamHumanCaseReceipt;
import com.latam.disruption.domain.model.UpstreamRebookingReceipt;
import com.latam.disruption.domain.service.AiSafetySandwichService;
import com.latam.disruption.domain.service.DisruptionDomainEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisruptionUseCasesTest {

    private FakePersistencePort persistencePort;
    private AtomicBoolean simulateUpstreamTimeout;
    private AiDraftResponse nextAiDraft;
    private AtomicInteger rebookingGatewayCalls;
    private DisruptionOrchestratorUseCase orchestrator;

    @BeforeEach
    void setUp() {
        persistencePort = new FakePersistencePort();
        simulateUpstreamTimeout = new AtomicBoolean(false);
        rebookingGatewayCalls = new AtomicInteger(0);
        nextAiDraft = new AiDraftResponse(
                "Tu vuelo LA500 tuvo un atraso de 160 minutos (puerta 24) y perderas la conexion "
                        + "LA2410 en Lima. Por politica POL-DELAY-2026-03 (R3) tienes cambio sin "
                        + "costo al vuelo LA2412 (opcion O1) y atencion prioritaria humana (R5).",
                "POL-DELAY-2026-03",
                "O1",
                0.94,
                true
        );

        TripContextPort tripPort = bookingId -> {
            if ("ABC123".equals(bookingId.value())) {
                return Optional.of(crearTripAbc123());
            }
            return Optional.empty();
        };

        OperationalEventsPort eventsPort = (bookingId, segmentIds) -> crearEventosAbc123();
        PolicyRulesPort policyPort = scenario -> crearPoliticasDelay();
        EligibleOptionsPort optionsPort = trip -> crearOpcionesAbc123();

        RebookingGatewayPort rebookingPort = (bookingId, option, key) -> {
            rebookingGatewayCalls.incrementAndGet();
            if (simulateUpstreamTimeout.get()) {
                throw new BusinessException(
                        TipoErrorNegocio.DEPENDENCIA_INDETERMINADA,
                        "UPSTREAM_TIMEOUT",
                        "Timeout al invocar create_rebooking_request; el resultado real es indeterminado"
                );
            }
            return new UpstreamRebookingReceipt(
                    "RBK-88231",
                    "PENDING_CONFIRMATION",
                    Instant.parse("2026-09-15T11:12:00Z")
            );
        };

        HumanCaseGatewayPort humanCasePort = (bookingId, reason, evidence) ->
                new UpstreamHumanCaseReceipt("HC-4471", "DISRUPTION_L2", 45);

        PassengerNotificationPort notificationPort = (bookingId, message, policyRef) -> {
            if (policyRef == null || policyRef.isBlank()) {
                throw new BusinessException(
                        TipoErrorNegocio.REGLA_POLITICA_INCUMPLIDA,
                        "UNVALIDATED_CONTENT",
                        "falta policy_ref"
                );
            }
            return new PassengerNotificationReceipt("NT-9912", "PUSH", "QUEUED");
        };

        AiInferencePort aiPort = promptContext -> nextAiDraft;

        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-15T11:03:00Z"), ZoneOffset.UTC);

        orchestrator = new DisruptionOrchestratorUseCase(
                tripPort,
                eventsPort,
                policyPort,
                optionsPort,
                rebookingPort,
                humanCasePort,
                notificationPort,
                aiPort,
                persistencePort,
                new DisruptionDomainEngine(),
                new AiSafetySandwichService(),
                fixedClock
        );
    }

    @Test
    void evaluacionDeViajeInexistenteLanzaNoEncontrado404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.assessTrip("ZZZ999"));
        assertEquals(TipoErrorNegocio.NO_ENCONTRADO, ex.getTipo());
    }

    @Test
    void evaluacionDeCasoBaseAbc123RegistraAuditoriaYRetornaDiagnosticoDeterminista() {
        DisruptionAssessment assessment = orchestrator.assessTrip("ABC123");

        assertEquals("ABC123", assessment.bookingId());
        assertTrue(assessment.missedConnection());
        assertEquals("O1", assessment.recommendedOption().optionId());
        assertEquals(1, persistencePort.auditLogs.size());
    }

    @Test
    void consultaConSolicitudDeCompensacionEnEfectivoActivaR4YCreaCasoHumano() {
        InquiryResult result = orchestrator.resolveInquiry(new InquiryCommand(
                "ABC123",
                "Exijo compensacion en dinero efectivo por perder mi vuelo a Mexico"
        ));

        assertTrue(result.requiresHumanEscalation());
        assertNotNull(result.humanCaseId());
        assertEquals("HC-4471", result.humanCaseId());
        assertEquals("POL-DELAY-2026-03", result.validatedPolicyRef());
    }

    @Test
    void consultaCuandoIaAlucinaVueloO2CaeAFallbackDeterministaYCreaCasoHumano() {
        nextAiDraft = new AiDraftResponse(
                "Toma el vuelo LA2450 (opcion O2) hoy a las 23:40.",
                "POL-DELAY-2026-03",
                "O2",
                0.96,
                false
        );

        InquiryResult result = orchestrator.resolveInquiry(new InquiryCommand(
                "ABC123",
                "¿Que opciones tengo para llegar a Mexico con mi hijo?"
        ));

        assertEquals("DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED", result.resolutionMode());
        assertEquals("UNELIGIBLE_OPTION_OR_FLIGHT_REFERENCED", result.guardrailRejectionReason());
        assertTrue(result.requiresHumanEscalation());
        assertNotNull(result.humanCaseId());
        assertFalse(result.message().contains("LA2450"));
    }

    @Test
    void rebookingExitosoConOpcionO1PersisteIdempotenciaYNotificaConPolicyRef() {
        RebookingResult first = orchestrator.createRebooking(
                new RebookingCommand("ABC123", "idem-rebook-0001", "O1"));

        assertEquals("RBK-88231", first.requestId());
        assertEquals("PENDING_CONFIRMATION", first.status());
        assertEquals("NT-9912", first.notificationId());
        assertFalse(first.replayedIdempotent());
        assertEquals(1, rebookingGatewayCalls.get());

        RebookingResult second = orchestrator.createRebooking(
                new RebookingCommand("ABC123", "idem-rebook-0001", "O1"));

        assertTrue(second.replayedIdempotent());
        assertEquals("RBK-88231", second.requestId());
        assertEquals(1, rebookingGatewayCalls.get());
    }

    @Test
    void rebookingReintentoConMismaLlavePeroDistintaOpcionLanzaConflicto409() {
        orchestrator.createRebooking(new RebookingCommand("ABC123", "idem-rebook-0002", "O1"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-rebook-0002", "O2")));
        assertEquals(TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA, ex.getTipo());
    }

    @Test
    void rebookingConOpcionO2SinAsientosSuficientesLanzaReglaIncumplida422() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-rebook-0003", "O2")));
        assertEquals(TipoErrorNegocio.REGLA_POLITICA_INCUMPLIDA, ex.getTipo());
        assertEquals(0, rebookingGatewayCalls.get());
    }

    @Test
    void rebookingAnteTimeoutUpstream503PersisteEstadoIndeterminadoYBloqueaOtraOpcion() {
        simulateUpstreamTimeout.set(true);

        BusinessException timeoutEx = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-timeout-0001", "O1")));
        assertEquals(TipoErrorNegocio.DEPENDENCIA_INDETERMINADA, timeoutEx.getTipo());

        Optional<RebookingRecord> saved = persistencePort.findRebookingByBookingAndKey(
                "ABC123", "idem-timeout-0001");
        assertTrue(saved.isPresent());
        assertEquals("INDETERMINATE_PENDING_RECONCILIATION", saved.get().status());

        simulateUpstreamTimeout.set(false);
        BusinessException conflictEx = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-new-key-0002", "O1")));
        assertEquals(TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA, conflictEx.getTipo());
    }

    @Test
    void escalamientoHumanoEsIdempotenteYProtegeDatosSensibles() {
        EscalationResult first = orchestrator.createHumanEscalation(
                new EscalationCommand("ABC123", "idem-human-0001",
                        "Asistencia prioritaria por grupo con menor y pernocta en Lima"));
        assertEquals("HC-4471", first.caseId());
        assertFalse(first.replayedIdempotent());

        EscalationResult replay = orchestrator.createHumanEscalation(
                new EscalationCommand("ABC123", "idem-human-0001",
                        "Asistencia prioritaria por grupo con menor y pernocta en Lima"));
        assertEquals("HC-4471", replay.caseId());
        assertTrue(replay.replayedIdempotent());
    }

    @Test
    void rebookingClaimFirstBloqueaPeticionConcurrenteEnProgresoSinInvocarUpstream() {
        persistencePort.saveRebooking(new RebookingRecord(
                java.util.UUID.randomUUID(),
                "ABC123",
                "idem-in-progress-0001",
                "O1",
                "LA2412",
                "POL-DELAY-2026-03",
                "IN_PROGRESS-idem-in-progress-0001",
                "IN_PROGRESS",
                null,
                Instant.parse("2026-09-15T11:18:00Z"),
                Instant.parse("2026-09-15T11:03:00Z")
        ));

        BusinessException exSameKey = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-in-progress-0001", "O1")));
        assertEquals(TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA, exSameKey.getTipo());
        assertEquals("REBOOKING_IN_PROGRESS", exSameKey.getCode());
        assertEquals(0, rebookingGatewayCalls.get());
    }

    @Test
    void consultasRepetidasSobreMismaReservaReutilizanCasoHumanoSinDuplicarCola() {
        InquiryResult first = orchestrator.resolveInquiry(new InquiryCommand(
                "ABC123",
                "¿Que opciones tengo para llegar a Mexico con mi hijo?"
        ));
        InquiryResult second = orchestrator.resolveInquiry(new InquiryCommand(
                "ABC123",
                "¿A que hora sale el proximo vuelo con mi hijo?"
        ));

        assertEquals(first.humanCaseId(), second.humanCaseId());
        assertEquals(1, persistencePort.humanCases.size());

        InquiryResult cashInquiry = orchestrator.resolveInquiry(new InquiryCommand(
                "ABC123",
                "¿Me corresponde compensacion en efectivo por la demora?"
        ));
        assertNotNull(cashInquiry.humanCaseId());
        assertEquals(2, persistencePort.humanCases.size());
    }

    @Test
    void rebookingPrevioEnEstadoFailedRechazaReplayCon409() {
        persistencePort.saveRebooking(new RebookingRecord(
                java.util.UUID.randomUUID(),
                "ABC123",
                "idem-failed-0001",
                "O1",
                "LA2412",
                "POL-DELAY-2026-03",
                "FAILED-idem-failed-0001",
                "FAILED",
                null,
                Instant.parse("2026-09-15T11:18:00Z"),
                Instant.parse("2026-09-15T11:03:00Z")
        ));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orchestrator.createRebooking(
                        new RebookingCommand("ABC123", "idem-failed-0001", "O1")));
        assertEquals(TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA, ex.getTipo());
        assertEquals("PREVIOUS_REBOOKING_FAILED", ex.getCode());
    }

    private static class FakePersistencePort implements DisruptionPersistencePort {
        private final List<RebookingRecord> rebookings = new ArrayList<>();
        private final List<HumanCaseRecord> humanCases = new ArrayList<>();
        private final List<AuditLogRecord> auditLogs = new ArrayList<>();

        @Override
        public Optional<RebookingRecord> findRebookingByBookingAndKey(String bookingId, String key) {
            return rebookings.stream()
                    .filter(r -> r.bookingId().equals(bookingId) && r.idempotencyKey().equals(key))
                    .findFirst();
        }

        @Override
        public List<RebookingRecord> findRebookingsByBookingId(String bookingId) {
            return rebookings.stream()
                    .filter(r -> r.bookingId().equals(bookingId))
                    .toList();
        }

        @Override
        public RebookingRecord saveRebooking(RebookingRecord record) {
            rebookings.removeIf(r -> r.id().equals(record.id()));
            rebookings.add(record);
            return record;
        }

        @Override
        public Optional<HumanCaseRecord> findHumanCaseByBookingAndKey(String bookingId, String key) {
            return humanCases.stream()
                    .filter(c -> c.bookingId().equals(bookingId) && c.idempotencyKey().equals(key))
                    .findFirst();
        }

        @Override
        public HumanCaseRecord saveHumanCase(HumanCaseRecord record) {
            humanCases.add(record);
            return record;
        }

        @Override
        public AuditLogRecord saveAuditLog(AuditLogRecord record) {
            auditLogs.add(record);
            return record;
        }
    }

    private static TripContext crearTripAbc123() {
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

    private static OperationalEventsSnapshot crearEventosAbc123() {
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

    private static PolicyRulesSnapshot crearPoliticasDelay() {
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

    private static EligibleOptionsSnapshot crearOpcionesAbc123() {
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
