package com.latam.disruption.infrastructure;

import com.latam.disruption.infrastructure.adapter.out.persistence.SpringDataAuditLogRepository;
import com.latam.disruption.infrastructure.adapter.out.persistence.SpringDataHumanCaseRepository;
import com.latam.disruption.infrastructure.adapter.out.persistence.SpringDataRebookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DisruptionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("disruption_test_db")
            .withUsername("latam_test")
            .withPassword("latam_test_pwd");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataRebookingRepository rebookingRepository;

    @Autowired
    private SpringDataHumanCaseRepository humanCaseRepository;

    @Autowired
    private SpringDataAuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanDatabase() {
        rebookingRepository.deleteAll();
        humanCaseRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void getEvaluacionesCasoBaseAbc123Retorna200YPersisteAuditoriaEnPostgres() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/evaluaciones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId", is("ABC123")))
                .andExpect(jsonPath("$.passengerCount", is(2)))
                .andExpect(jsonPath("$.includesMinor", is(true)))
                .andExpect(jsonPath("$.relevantModification", is(true)))
                .andExpect(jsonPath("$.missedConnection", is(true)))
                .andExpect(jsonPath("$.primaryDelayMinutes", is(160)))
                .andExpect(jsonPath("$.updatedDepartureTime", is("17:10")))
                .andExpect(jsonPath("$.updatedGate", is("24")))
                .andExpect(jsonPath("$.discardedSources", hasItem("LEGACY_MIRROR")))
                .andExpect(jsonPath("$.policyVersion", is("POL-DELAY-2026-03")))
                .andExpect(jsonPath("$.appliedRuleIds", hasItem("R3")))
                .andExpect(jsonPath("$.appliedRuleIds", hasItem("R5")))
                .andExpect(jsonPath("$.requiresHumanApproval", is(true)))
                .andExpect(jsonPath("$.overnightRequired", is(true)))
                .andExpect(jsonPath("$.blockedByMissingPenaltyAmount", is(false)))
                .andExpect(jsonPath("$.recommendedOptionId", is("O1")))
                .andExpect(jsonPath("$.recommendedFlightNumber", is("LA2412")));

        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void getEvaluacionesReservaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/ZZZ999/evaluaciones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("BOOKING_NOT_FOUND")))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void getEvaluacionesCuandoTodasLasFuentesEstanDegradadasRetorna503() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/ALLDEG/evaluaciones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("ALL_OPERATIONAL_SOURCES_DEGRADED")))
                .andExpect(jsonPath("$.status", is(503)));
    }

    @Test
    void getEvaluacionesCuandoHayConflictoEntreFuentesSaludablesActivaRevisionHumana() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/CONFL1/evaluaciones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceConflictDetected", is(true)))
                .andExpect(jsonPath("$.requiresHumanApproval", is(true)));
    }

    @Test
    void postConsultasCasoBaseValidaSandwichIaYCreaCasoHumanoPorMenorEnPernocta() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Que opciones tengo para llegar a Mexico con mi hijo?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId", is("ABC123")))
                .andExpect(jsonPath("$.resolutionMode", is("AI_ASSISTED_VALIDATED")))
                .andExpect(jsonPath("$.validatedPolicyRef", is("POL-DELAY-2026-03")))
                .andExpect(jsonPath("$.recommendedOptionId", is("O1")))
                .andExpect(jsonPath("$.requiresHumanEscalation", is(true)))
                .andExpect(jsonPath("$.humanCaseId", notNullValue()));

        assertEquals(1, humanCaseRepository.count());
    }

    @Test
    void postConsultasDetectaAlucinacionDeVueloO2YCaeAFallbackDeterminista() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Quiero viajar hoy [SIMULATE_HALLUCINATION_O2]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionMode", is("DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED")))
                .andExpect(jsonPath("$.guardrailRejectionReason", is("UNELIGIBLE_OPTION_OR_FLIGHT_REFERENCED")))
                .andExpect(jsonPath("$.requiresHumanEscalation", is(true)))
                .andExpect(jsonPath("$.message", containsString("LA2412")));
    }

    @Test
    void postConsultasDetectaBajaConfianzaYCaeAFallbackDeterminista() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Duda general [SIMULATE_LOW_CONFIDENCE]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionMode", is("DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED")))
                .andExpect(jsonPath("$.guardrailRejectionReason", is("LOW_CONFIDENCE_BELOW_THRESHOLD")))
                .andExpect(jsonPath("$.requiresHumanEscalation", is(true)));
    }

    @Test
    void postConsultasDetectaPromesaDeEfectivoNoAutorizadaYCaeAFallbackDeterminista() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Quiero dinero [SIMULATE_CASH_PROMISE]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionMode", is("DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED")))
                .andExpect(jsonPath("$.guardrailRejectionReason", is("UNAUTHORIZED_CASH_COMPENSATION_PROMISE")))
                .andExpect(jsonPath("$.requiresHumanEscalation", is(true)));
    }

    @Test
    void postReubicacionesGarantizaIdempotenciaRealEnPostgreSQLYRechazaConflicto409() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", "idem-pg-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId", is("ABC123")))
                .andExpect(jsonPath("$.optionId", is("O1")))
                .andExpect(jsonPath("$.flightNumber", is("LA2412")))
                .andExpect(jsonPath("$.status", is("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.policyRef", is("POL-DELAY-2026-03")))
                .andExpect(jsonPath("$.replayedIdempotent", is(false)));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", "idem-pg-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayedIdempotent", is(true)));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", "idem-pg-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_PAYLOAD_MISMATCH")));

        assertEquals(1, rebookingRepository.count());
    }

    @Test
    void postReubicacionesConOpcionO2SinCupoSuficienteRetorna422() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", "idem-pg-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O2\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("OPTION_NOT_ELIGIBLE")));
    }

    @Test
    void postReubicacionesConCatalogoVencidoEnExp123Retorna422() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/EXP123/reubicaciones")
                        .header("Idempotency-Key", "idem-exp-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("OPTION_NOT_ELIGIBLE")))
                .andExpect(jsonPath("$.message", containsString("OPTION_TTL_EXPIRED")));
    }

    @Test
    void postReubicacionesAnteTimeoutUpstreamEnTim503Retorna503YBloqueaNuevaSolicitud() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/TIM503/reubicaciones")
                        .header("Idempotency-Key", "idem-tim-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("UPSTREAM_TIMEOUT")));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/TIM503/reubicaciones")
                        .header("Idempotency-Key", "idem-tim-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("UPSTREAM_TIMEOUT_PENDING_RECONCILIATION")));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/TIM503/reubicaciones")
                        .header("Idempotency-Key", "idem-tim-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_REBOOKING_ALREADY_ACTIVE_OR_INDETERMINATE")));
    }

    @Test
    void postEscalamientosGarantizaIdempotenciaYDetectaConflicto409() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/escalamientos")
                        .header("Idempotency-Key", "idem-esc-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Grupo familiar con menor requiere hotel en Lima\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId", is("ABC123")))
                .andExpect(jsonPath("$.queue", is("DISRUPTION_L2")))
                .andExpect(jsonPath("$.slaMinutes", is(45)))
                .andExpect(jsonPath("$.replayedIdempotent", is(false)));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/escalamientos")
                        .header("Idempotency-Key", "idem-esc-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Grupo familiar con menor requiere hotel en Lima\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayedIdempotent", is(true)));

        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/escalamientos")
                        .header("Idempotency-Key", "idem-esc-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Motivo distinto con misma llave de idempotencia\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_PAYLOAD_MISMATCH")));
    }
}
