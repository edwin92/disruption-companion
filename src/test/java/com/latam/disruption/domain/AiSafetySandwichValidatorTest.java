package com.latam.disruption.domain;

import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AnonymizedAiPromptContext;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.GuardedInquiryResolution;
import com.latam.disruption.domain.model.TripContext;
import com.latam.disruption.domain.service.AiSafetySandwichService;
import com.latam.disruption.domain.service.DisruptionDomainEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSafetySandwichValidatorTest {

    private DisruptionDomainEngine engine;
    private AiSafetySandwichService sandwichService;
    private TripContext trip;
    private DisruptionAssessment baseAssessment;

    @BeforeEach
    void setUp() {
        engine = new DisruptionDomainEngine();
        sandwichService = new AiSafetySandwichService();
        trip = DisruptionDomainEngineTest.crearTripAbc123();
        baseAssessment = engine.assess(
                trip,
                DisruptionDomainEngineTest.crearEventosAbc123(),
                DisruptionDomainEngineTest.crearPoliticasDelay(),
                DisruptionDomainEngineTest.crearOpcionesAbc123(),
                false,
                Instant.parse("2026-09-15T11:03:00Z")
        );
    }

    @Test
    void contextoEnviadoAlModeloEliminaTodoPiiYCalibraTemperaturaCero() {
        String rawQuestion = "Soy María Silva RUT 12.345.678-9 correo maria.silva@example.com "
                + "tel +56 9 8765 4321, ¿qué pasó con mi conexión en Lima?";

        AnonymizedAiPromptContext promptContext = sandwichService.buildSanitizedPromptContext(
                trip, baseAssessment, rawQuestion);

        assertEquals(0.0, promptContext.temperature());
        assertEquals(0.85, promptContext.minimumConfidenceThreshold());
        assertFalse(promptContext.sanitizedQuestion().contains("12.345.678-9"));
        assertFalse(promptContext.sanitizedQuestion().contains("maria.silva@example.com"));
        assertFalse(promptContext.sanitizedQuestion().contains("8765 4321"));
        assertFalse(promptContext.sanitizedQuestion().contains("María Silva"));
        assertFalse(promptContext.systemFactsSummary().contains("12.345.678-9"));
        assertFalse(promptContext.systemFactsSummary().contains("maria.silva@example.com"));
        assertFalse(promptContext.systemFactsSummary().contains("María Silva"));
        assertFalse(promptContext.systemFactsSummary().contains("Tomás Silva"));
    }

    @Test
    void detectorDeterministaIdentificaSolicitudDeCompensacionEnDineroYActivaR4() {
        assertTrue(sandwichService.detectsCashCompensationRequest(
                "Quiero pedir una compensación en efectivo e indemnización económica por el atraso"));
        assertFalse(sandwichService.detectsCashCompensationRequest(
                "¿A qué puerta debo ir y qué pasa con mi vuelo a México?"));
    }

    @Test
    void guardiaDeSalidaAceptaRespuestaDeIaValidaConAltaConfianzaYPoliticaOficial() {
        AiDraftResponse validDraft = new AiDraftResponse(
                "Tu vuelo LA500 presenta una demora de 160 minutos (puerta 24), lo que impide conectar "
                        + "con el vuelo LA2410 en Lima. Según la política POL-DELAY-2026-03 (regla R3), "
                        + "tienes derecho a cambio sin costo a la opción O1 (vuelo LA2412 del 2026-09-16 "
                        + "a las 08:15) y asistencia prioritaria humana (regla R5).",
                "POL-DELAY-2026-03",
                "O1",
                0.93,
                true
        );

        GuardedInquiryResolution resolution = sandwichService.validateAndGuardOutput(
                baseAssessment, validDraft);

        assertTrue(resolution.aiOutputAccepted());
        assertEquals("AI_ASSISTED_VALIDATED", resolution.resolutionMode());
        assertEquals("POL-DELAY-2026-03", resolution.validatedPolicyRef());
        assertTrue(resolution.requiresHumanEscalation());
    }

    @Test
    void guardiaDeSalidaBloqueaIaSiMencionaVueloDescartadoO2SinAsientos() {
        AiDraftResponse hallucinatedDraft = new AiDraftResponse(
                "Puedes tomar hoy mismo el vuelo LA2450 (opción O2) a las 23:40 sin problema.",
                "POL-DELAY-2026-03",
                "O2",
                0.95,
                false
        );

        GuardedInquiryResolution resolution = sandwichService.validateAndGuardOutput(
                baseAssessment, hallucinatedDraft);

        assertFalse(resolution.aiOutputAccepted());
        assertEquals("DETERMINISTIC_FALLBACK_GUARDRAIL_TRIGGERED", resolution.resolutionMode());
        assertEquals("UNELIGIBLE_OPTION_OR_FLIGHT_REFERENCED", resolution.guardrailRejectionReason());
        assertTrue(resolution.finalPassengerMessage().contains("LA2412"));
        assertFalse(resolution.finalPassengerMessage().contains("LA2450"));
    }

    @Test
    void guardiaDeSalidaBloqueaIaSiFaltaPolicyRefOEsVersionObsoleta() {
        AiDraftResponse unvalidatedPolicyDraft = new AiDraftResponse(
                "Tu vuelo se atrasó y te reubicaremos en el vuelo LA2412.",
                "POL-DELAY-2025-11",
                "O1",
                0.91,
                true
        );

        GuardedInquiryResolution resolution = sandwichService.validateAndGuardOutput(
                baseAssessment, unvalidatedPolicyDraft);

        assertFalse(resolution.aiOutputAccepted());
        assertEquals("INVALID_OR_MISSING_POLICY_REF", resolution.guardrailRejectionReason());
        assertEquals("POL-DELAY-2026-03", resolution.validatedPolicyRef());
    }

    @Test
    void guardiaDeSalidaBloqueaIaSiConfianzaEsMenorAlUmbral085() {
        AiDraftResponse lowConfidenceDraft = new AiDraftResponse(
                "Tu vuelo LA500 está demorado y recomendamos el vuelo LA2412 bajo POL-DELAY-2026-03.",
                "POL-DELAY-2026-03",
                "O1",
                0.72,
                true
        );

        GuardedInquiryResolution resolution = sandwichService.validateAndGuardOutput(
                baseAssessment, lowConfidenceDraft);

        assertFalse(resolution.aiOutputAccepted());
        assertEquals("LOW_CONFIDENCE_BELOW_THRESHOLD", resolution.guardrailRejectionReason());
        assertTrue(resolution.requiresHumanEscalation());
    }
}
