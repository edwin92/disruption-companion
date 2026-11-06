package com.latam.disruption.infrastructure.adapter.out.ai;

import com.latam.disruption.application.port.out.AiInferencePort;
import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AnonymizedAiPromptContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DeterministicCalibratedAiAdapter implements AiInferencePort {

    private static final double HIGH_CONFIDENCE = 0.94;
    private static final double LOW_CONFIDENCE = 0.52;

    private final boolean simulationTriggersEnabled;

    public DeterministicCalibratedAiAdapter() {
        this(true);
    }

    @Autowired
    public DeterministicCalibratedAiAdapter(
            @Value("${disruption.ai.simulation-triggers-enabled:true}") boolean simulationTriggersEnabled
    ) {
        this.simulationTriggersEnabled = simulationTriggersEnabled;
    }

    @Override
    public AiDraftResponse inferInquiryDraft(AnonymizedAiPromptContext promptContext) {
        String question = promptContext.sanitizedQuestion() != null
                ? promptContext.sanitizedQuestion()
                : "";
        String upper = question.toUpperCase(Locale.ROOT);

        if (simulationTriggersEnabled) {
            if (upper.contains("[SIMULATE_HALLUCINATION_O2]")) {
                return new AiDraftResponse(
                        "Te recomendamos tomar el vuelo LA2450 (opcion O2) hoy a las 23:40.",
                        promptContext.policyVersion(),
                        "O2",
                        0.96,
                        false
                );
            }

            if (upper.contains("[SIMULATE_LOW_CONFIDENCE]")) {
                return new AiDraftResponse(
                        "Podrias revisar opciones en el aeropuerto segun disponibilidad.",
                        promptContext.policyVersion(),
                        "O1",
                        LOW_CONFIDENCE,
                        true
                );
            }

            if (upper.contains("[SIMULATE_CASH_PROMISE]")) {
                return new AiDraftResponse(
                        "Tu transferencia aprobada de efectivo inmediato ya fue procesada.",
                        promptContext.policyVersion(),
                        "O1",
                        0.91,
                        false
                );
            }
        }

        boolean requiresHuman = promptContext.systemFactsSummary().contains("RequiresHumanApproval=true");
        boolean cashRequested = promptContext.systemFactsSummary().contains("R4");
        String appliedRulesText = cashRequested ? "reglas R3, R4 y R5" : "reglas R3 y R5";
        String cashSuffix = cashRequested
                ? " Respecto a tu solicitud de compensacion en efectivo (regla R4), "
                + "las compensaciones monetarias no se aprueban automaticamente y quedaron "
                + "asociadas a tu caso de revision humana especializada."
                : "";

        String explanation = "De acuerdo con la verificacion operativa de la reserva "
                + promptContext.bookingId()
                + ", el tramo inicial registra una demora verificada de 160 minutos en puerta 24, "
                + "lo que impide conectar con el vuelo LA2410 en Lima. "
                + "Bajo la politica vigente " + promptContext.policyVersion()
                + " (" + appliedRulesText + "), tienes derecho a cambio sin costo hacia la opcion O1 "
                + "(vuelo LA2412 del 2026-09-16 a las 08:15 con 4 asientos disponibles) "
                + "y asistencia prioritaria de un especialista humano para coordinar la pernocta "
                + "del grupo familiar." + cashSuffix;

        return new AiDraftResponse(
                explanation,
                promptContext.policyVersion(),
                "O1",
                HIGH_CONFIDENCE,
                requiresHuman
        );
    }
}
