package com.latam.disruption.application.usecase;

import com.latam.disruption.application.dto.EscalationCommand;
import com.latam.disruption.application.dto.EscalationResult;
import com.latam.disruption.application.dto.InquiryCommand;
import com.latam.disruption.application.dto.InquiryResult;
import com.latam.disruption.application.dto.RebookingCommand;
import com.latam.disruption.application.dto.RebookingResult;
import com.latam.disruption.application.port.in.AssessTripDisruptionUseCase;
import com.latam.disruption.application.port.in.CreateHumanEscalationUseCase;
import com.latam.disruption.application.port.in.CreateRebookingUseCase;
import com.latam.disruption.application.port.in.ResolvePassengerInquiryUseCase;
import com.latam.disruption.application.port.out.AiInferencePort;
import com.latam.disruption.application.port.out.DisruptionPersistencePort;
import com.latam.disruption.application.port.out.EligibleOptionsPort;
import com.latam.disruption.application.port.out.HumanCaseGatewayPort;
import com.latam.disruption.application.port.out.OperationalEventsPort;
import com.latam.disruption.application.port.out.PassengerNotificationPort;
import com.latam.disruption.application.port.out.PolicyRulesPort;
import com.latam.disruption.application.port.out.RebookingGatewayPort;
import com.latam.disruption.application.port.out.TripContextPort;
import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AnonymizedAiPromptContext;
import com.latam.disruption.domain.model.AuditLogRecord;
import com.latam.disruption.domain.model.BookingId;
import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.FlightSegment;
import com.latam.disruption.domain.model.GuardedInquiryResolution;
import com.latam.disruption.domain.model.HumanCaseRecord;
import com.latam.disruption.domain.model.IdempotencyKey;
import com.latam.disruption.domain.model.OperationalEventsSnapshot;
import com.latam.disruption.domain.model.OptionViability;
import com.latam.disruption.domain.model.PassengerNotificationReceipt;
import com.latam.disruption.domain.model.PolicyRulesSnapshot;
import com.latam.disruption.domain.model.RebookingRecord;
import com.latam.disruption.domain.model.TripContext;
import com.latam.disruption.domain.model.UpstreamHumanCaseReceipt;
import com.latam.disruption.domain.model.UpstreamRebookingReceipt;
import com.latam.disruption.domain.service.AiSafetySandwichService;
import com.latam.disruption.domain.service.DisruptionDomainEngine;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class DisruptionOrchestratorUseCase implements
        AssessTripDisruptionUseCase,
        ResolvePassengerInquiryUseCase,
        CreateRebookingUseCase,
        CreateHumanEscalationUseCase {

    private static final Pattern OPTION_ID_PATTERN = Pattern.compile("^O[0-9]{1,3}$");

    private final TripContextPort tripContextPort;
    private final OperationalEventsPort operationalEventsPort;
    private final PolicyRulesPort policyRulesPort;
    private final EligibleOptionsPort eligibleOptionsPort;
    private final RebookingGatewayPort rebookingGatewayPort;
    private final HumanCaseGatewayPort humanCaseGatewayPort;
    private final PassengerNotificationPort passengerNotificationPort;
    private final AiInferencePort aiInferencePort;
    private final DisruptionPersistencePort persistencePort;
    private final DisruptionDomainEngine domainEngine;
    private final AiSafetySandwichService aiSafetySandwichService;
    private final Clock clock;

    public DisruptionOrchestratorUseCase(
            TripContextPort tripContextPort,
            OperationalEventsPort operationalEventsPort,
            PolicyRulesPort policyRulesPort,
            EligibleOptionsPort eligibleOptionsPort,
            RebookingGatewayPort rebookingGatewayPort,
            HumanCaseGatewayPort humanCaseGatewayPort,
            PassengerNotificationPort passengerNotificationPort,
            AiInferencePort aiInferencePort,
            DisruptionPersistencePort persistencePort,
            DisruptionDomainEngine domainEngine,
            AiSafetySandwichService aiSafetySandwichService,
            Clock clock
    ) {
        this.tripContextPort = tripContextPort;
        this.operationalEventsPort = operationalEventsPort;
        this.policyRulesPort = policyRulesPort;
        this.eligibleOptionsPort = eligibleOptionsPort;
        this.rebookingGatewayPort = rebookingGatewayPort;
        this.humanCaseGatewayPort = humanCaseGatewayPort;
        this.passengerNotificationPort = passengerNotificationPort;
        this.aiInferencePort = aiInferencePort;
        this.persistencePort = persistencePort;
        this.domainEngine = domainEngine;
        this.aiSafetySandwichService = aiSafetySandwichService;
        this.clock = clock;
    }

    @Override
    public DisruptionAssessment assessTrip(String rawBookingId) {
        BookingId bookingId = new BookingId(rawBookingId);
        TripContext trip = loadTripOrThrow(bookingId);
        DisruptionAssessment assessment = assessForTrip(bookingId, trip, false);
        persistencePort.saveAuditLog(new AuditLogRecord(
                UUID.randomUUID(),
                bookingId.value(),
                "ASSESS_DISRUPTION",
                "DETERMINISTIC_ENGINE",
                assessment.policyVersion(),
                null,
                assessment.requiresHumanApproval(),
                assessment.passengerExplanation(),
                Instant.now(clock)
        ));
        return assessment;
    }

    @Override
    public InquiryResult resolveInquiry(InquiryCommand command) {
        if (command == null) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_INQUIRY_COMMAND",
                    "La consulta del pasajero es obligatoria"
            );
        }
        BookingId bookingId = new BookingId(command.bookingId());
        String question = validateQuestion(command.question());
        boolean compensationRequested = aiSafetySandwichService.detectsCashCompensationRequest(question);

        TripContext trip = loadTripOrThrow(bookingId);
        DisruptionAssessment assessment = assessForTrip(bookingId, trip, compensationRequested);
        AnonymizedAiPromptContext promptContext = aiSafetySandwichService.buildSanitizedPromptContext(
                trip, assessment, question);
        AiDraftResponse aiDraft = aiInferencePort.inferInquiryDraft(promptContext);
        GuardedInquiryResolution guarded = aiSafetySandwichService.validateAndGuardOutput(assessment, aiDraft);

        String humanCaseId = null;
        if (guarded.requiresHumanEscalation()) {
            String autoEscalationKey = "auto-inq-" + bookingId.value() + (compensationRequested ? "-cash" : "");
            Optional<HumanCaseRecord> existingCase = persistencePort.findHumanCaseByBookingAndKey(
                    bookingId.value(), autoEscalationKey);
            if (existingCase.isPresent()) {
                humanCaseId = existingCase.get().caseId();
            } else {
                String reason = buildEscalationReason(assessment, compensationRequested);
                String sanitizedEvidence = buildSanitizedEvidence(assessment, guarded.resolutionMode());
                UpstreamHumanCaseReceipt receipt = humanCaseGatewayPort.createHumanCase(
                        bookingId.value(), reason, sanitizedEvidence);
                humanCaseId = receipt.caseId();
                persistencePort.saveHumanCase(new HumanCaseRecord(
                        UUID.randomUUID(),
                        bookingId.value(),
                        autoEscalationKey,
                        receipt.caseId(),
                        receipt.queue(),
                        receipt.slaMinutes(),
                        reason,
                        sanitizedEvidence,
                        Instant.now(clock)
                ));
            }
        }

        persistencePort.saveAuditLog(new AuditLogRecord(
                UUID.randomUUID(),
                bookingId.value(),
                "RESOLVE_INQUIRY",
                guarded.resolutionMode(),
                guarded.validatedPolicyRef(),
                guarded.confidenceScore(),
                guarded.requiresHumanEscalation(),
                guarded.finalPassengerMessage(),
                Instant.now(clock)
        ));

        return new InquiryResult(
                bookingId.value(),
                guarded.resolutionMode(),
                guarded.validatedPolicyRef(),
                guarded.recommendedOptionId(),
                guarded.confidenceScore(),
                guarded.requiresHumanEscalation(),
                humanCaseId,
                guarded.guardrailRejectionReason(),
                guarded.finalPassengerMessage()
        );
    }

    @Override
    public RebookingResult createRebooking(RebookingCommand command) {
        if (command == null) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_REBOOKING_COMMAND",
                    "La solicitud de reacomodacion es obligatoria"
            );
        }
        BookingId bookingId = new BookingId(command.bookingId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String optionId = validateOptionId(command.optionId());

        Optional<RebookingRecord> existingByKey = persistencePort.findRebookingByBookingAndKey(
                bookingId.value(), idempotencyKey.value());
        if (existingByKey.isPresent()) {
            RebookingRecord previous = existingByKey.get();
            if (!previous.optionId().equals(optionId)) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "La llave de idempotencia ya fue utilizada con una opcion distinta"
                );
            }
            if ("IN_PROGRESS".equals(previous.status())) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "REBOOKING_IN_PROGRESS",
                        "Existe una solicitud en curso para esta llave de idempotencia"
                );
            }
            if ("FAILED".equals(previous.status())) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "PREVIOUS_REBOOKING_FAILED",
                        "La solicitud previa con esta llave finalizo en estado fallido"
                );
            }
            if ("INDETERMINATE_PENDING_RECONCILIATION".equals(previous.status())) {
                throw new BusinessException(
                        TipoErrorNegocio.DEPENDENCIA_INDETERMINADA,
                        "UPSTREAM_TIMEOUT_PENDING_RECONCILIATION",
                        "La solicitud previa se encuentra en estado indeterminado pendiente de reconciliacion"
                );
            }
            return new RebookingResult(
                    previous.upstreamRequestId(),
                    previous.bookingId(),
                    previous.optionId(),
                    previous.flightNumber(),
                    previous.status(),
                    previous.policyRef(),
                    previous.notificationId(),
                    previous.expiresAt(),
                    true
            );
        }

        List<RebookingRecord> existingForBooking = persistencePort.findRebookingsByBookingId(bookingId.value());
        for (RebookingRecord record : existingForBooking) {
            if ("IN_PROGRESS".equals(record.status())
                    || "PENDING_CONFIRMATION".equals(record.status())
                    || "CONFIRMED".equals(record.status())
                    || "INDETERMINATE_PENDING_RECONCILIATION".equals(record.status())) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "BOOKING_REBOOKING_ALREADY_ACTIVE_OR_INDETERMINATE",
                        "Ya existe una reacomodacion en curso o pendiente de reconciliacion para la reserva"
                );
            }
        }

        TripContext trip = loadTripOrThrow(bookingId);
        DisruptionAssessment assessment = assessForTrip(bookingId, trip, false);
        OptionViability matchedOption = assessment.evaluatedOptions().stream()
                .filter(ov -> ov.option().optionId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        TipoErrorNegocio.REGLA_POLITICA_INCUMPLIDA,
                        "OPTION_NOT_FOUND_OR_INELIGIBLE",
                        "La opcion solicitada no existe dentro del catalogo elegible de la reserva"
                ));

        if (!matchedOption.eligible() || !"REBOOK".equalsIgnoreCase(matchedOption.option().type())) {
            throw new BusinessException(
                    TipoErrorNegocio.REGLA_POLITICA_INCUMPLIDA,
                    "OPTION_NOT_ELIGIBLE",
                    "La opcion solicitada incumple reglas de elegibilidad: "
                            + String.join(", ", matchedOption.rejectionReasons())
            );
        }

        CandidateOption candidate = matchedOption.option();
        Instant now = Instant.now(clock);
        String resolvedFlight = candidate.flightNumber() != null ? candidate.flightNumber() : "UNKNOWN";

        RebookingRecord claimed = persistencePort.saveRebooking(new RebookingRecord(
                UUID.randomUUID(),
                bookingId.value(),
                idempotencyKey.value(),
                candidate.optionId(),
                resolvedFlight,
                candidate.policyRef(),
                "IN_PROGRESS-" + idempotencyKey.value(),
                "IN_PROGRESS",
                null,
                now.plusSeconds(900),
                now
        ));

        UpstreamRebookingReceipt receipt;
        try {
            receipt = rebookingGatewayPort.createRebookingRequest(
                    bookingId.value(), candidate, idempotencyKey);
        } catch (BusinessException ex) {
            String targetStatus = ex.getTipo() == TipoErrorNegocio.DEPENDENCIA_INDETERMINADA
                    ? "INDETERMINATE_PENDING_RECONCILIATION"
                    : "FAILED";
            persistencePort.saveRebooking(new RebookingRecord(
                    claimed.id(),
                    bookingId.value(),
                    idempotencyKey.value(),
                    candidate.optionId(),
                    resolvedFlight,
                    candidate.policyRef(),
                    (targetStatus.startsWith("INDETERMINATE") ? "INDETERMINATE-" : "FAILED-") + idempotencyKey.value(),
                    targetStatus,
                    null,
                    now.plusSeconds(900),
                    now
            ));
            persistencePort.saveAuditLog(new AuditLogRecord(
                    UUID.randomUUID(),
                    bookingId.value(),
                    "CREATE_REBOOKING_" + targetStatus,
                    "ERROR_GUARD",
                    candidate.policyRef(),
                    null,
                    true,
                    "Fallo upstream: " + ex.getMessage(),
                    now
            ));
            throw ex;
        } catch (RuntimeException ex) {
            persistencePort.saveRebooking(new RebookingRecord(
                    claimed.id(),
                    bookingId.value(),
                    idempotencyKey.value(),
                    candidate.optionId(),
                    resolvedFlight,
                    candidate.policyRef(),
                    "INDETERMINATE-" + idempotencyKey.value(),
                    "INDETERMINATE_PENDING_RECONCILIATION",
                    null,
                    now.plusSeconds(900),
                    now
            ));
            persistencePort.saveAuditLog(new AuditLogRecord(
                    UUID.randomUUID(),
                    bookingId.value(),
                    "CREATE_REBOOKING_RUNTIME_EXCEPTION",
                    "INDETERMINATE_STATE_GUARD",
                    candidate.policyRef(),
                    null,
                    true,
                    "Error de runtime upstream: " + ex.getMessage(),
                    now
            ));
            throw ex;
        }

        RebookingRecord saved = persistencePort.saveRebooking(new RebookingRecord(
                claimed.id(),
                bookingId.value(),
                idempotencyKey.value(),
                candidate.optionId(),
                candidate.flightNumber(),
                candidate.policyRef(),
                receipt.requestId(),
                receipt.status(),
                null,
                receipt.expiresAt(),
                now
        ));

        String notificationId = null;
        try {
            String notificationMessage = "Solicitud de reacomodacion " + receipt.requestId()
                    + " registrada para vuelo " + candidate.flightNumber()
                    + " bajo politica " + candidate.policyRef() + ".";
            PassengerNotificationReceipt notificationReceipt = passengerNotificationPort.notifyPassenger(
                    bookingId.value(),
                    notificationMessage,
                    candidate.policyRef()
            );
            notificationId = notificationReceipt.notificationId();
            saved = persistencePort.saveRebooking(new RebookingRecord(
                    saved.id(),
                    saved.bookingId(),
                    saved.idempotencyKey(),
                    saved.optionId(),
                    saved.flightNumber(),
                    saved.policyRef(),
                    saved.upstreamRequestId(),
                    saved.status(),
                    notificationId,
                    saved.expiresAt(),
                    saved.createdAt()
            ));
        } catch (Exception ex) {
            // Notificacion fallo pero la reserva quedo confirmada en upstream
        }

        persistencePort.saveAuditLog(new AuditLogRecord(
                UUID.randomUUID(),
                bookingId.value(),
                "CREATE_REBOOKING",
                "DETERMINISTIC_REBOOKING",
                candidate.policyRef(),
                null,
                assessment.requiresHumanApproval(),
                "Reacomodacion creada: " + receipt.requestId() + " opcion " + candidate.optionId(),
                now
        ));

        return new RebookingResult(
                saved.upstreamRequestId(),
                saved.bookingId(),
                saved.optionId(),
                saved.flightNumber(),
                saved.status(),
                saved.policyRef(),
                saved.notificationId(),
                saved.expiresAt(),
                false
        );
    }

    @Override
    public EscalationResult createHumanEscalation(EscalationCommand command) {
        if (command == null) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_ESCALATION_COMMAND",
                    "La solicitud de escalamiento humano es obligatoria"
            );
        }
        BookingId bookingId = new BookingId(command.bookingId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String reason = validateReason(command.reason());

        Optional<HumanCaseRecord> existing = persistencePort.findHumanCaseByBookingAndKey(
                bookingId.value(), idempotencyKey.value());
        if (existing.isPresent()) {
            HumanCaseRecord previous = existing.get();
            if (!previous.reason().equals(reason)) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "La llave de idempotencia ya fue utilizada con un motivo de escalamiento distinto"
                );
            }
            return new EscalationResult(
                    previous.caseId(),
                    previous.bookingId(),
                    previous.queueName(),
                    previous.slaMinutes(),
                    previous.reason(),
                    true
            );
        }

        TripContext trip = loadTripOrThrow(bookingId);
        DisruptionAssessment assessment = assessForTrip(bookingId, trip, false);
        String sanitizedEvidence = buildSanitizedEvidence(assessment, "EXPLICIT_HUMAN_ESCALATION");
        UpstreamHumanCaseReceipt receipt = humanCaseGatewayPort.createHumanCase(
                bookingId.value(), reason, sanitizedEvidence);

        Instant now = Instant.now(clock);
        persistencePort.saveHumanCase(new HumanCaseRecord(
                UUID.randomUUID(),
                bookingId.value(),
                idempotencyKey.value(),
                receipt.caseId(),
                receipt.queue(),
                receipt.slaMinutes(),
                reason,
                sanitizedEvidence,
                now
        ));

        persistencePort.saveAuditLog(new AuditLogRecord(
                UUID.randomUUID(),
                bookingId.value(),
                "CREATE_HUMAN_ESCALATION",
                "HUMAN_CONTROLLED",
                assessment.policyVersion(),
                null,
                true,
                "Caso humano creado: " + receipt.caseId() + " cola " + receipt.queue(),
                now
        ));

        return new EscalationResult(
                receipt.caseId(),
                bookingId.value(),
                receipt.queue(),
                receipt.slaMinutes(),
                reason,
                false
        );
    }

    private TripContext loadTripOrThrow(BookingId bookingId) {
        return tripContextPort.getTripContext(bookingId)
                .orElseThrow(() -> new BusinessException(
                        TipoErrorNegocio.NO_ENCONTRADO,
                        "BOOKING_NOT_FOUND",
                        "No se encontro contexto de viaje para la reserva solicitada"
                ));
    }

    private DisruptionAssessment assessForTrip(
            BookingId bookingId,
            TripContext trip,
            boolean compensationRequested
    ) {
        List<String> segmentIds = trip.segments().stream()
                .map(FlightSegment::segmentId)
                .toList();

        OperationalEventsSnapshot events = operationalEventsPort.getOperationalEvents(
                bookingId.value(), segmentIds);
        PolicyRulesSnapshot policies = policyRulesPort.getPolicyRules("DELAY");
        EligibleOptionsSnapshot options = eligibleOptionsPort.getEligibleOptions(trip);

        return domainEngine.assess(trip, events, policies, options, compensationRequested, Instant.now(clock));
    }

    private String validateQuestion(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "EMPTY_PASSENGER_QUESTION",
                    "La pregunta del pasajero no puede ser vacia"
            );
        }
        String trimmed = rawQuestion.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "PASSENGER_QUESTION_TOO_LONG",
                    "La pregunta del pasajero supera el maximo permitido de 500 caracteres"
            );
        }
        return trimmed;
    }

    private String validateOptionId(String rawOptionId) {
        if (rawOptionId == null || rawOptionId.isBlank()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_OPTION_ID",
                    "El identificador de opcion es obligatorio"
            );
        }
        String normalized = rawOptionId.trim();
        if (!OPTION_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_OPTION_ID_FORMAT",
                    "El identificador de opcion debe cumplir el formato O1..O999"
            );
        }
        return normalized;
    }

    private String validateReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "EMPTY_ESCALATION_REASON",
                    "El motivo de escalamiento humano es obligatorio"
            );
        }
        String trimmed = rawReason.trim();
        if (trimmed.length() > 300) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "ESCALATION_REASON_TOO_LONG",
                    "El motivo de escalamiento humano supera el maximo de 300 caracteres"
            );
        }
        return trimmed;
    }

    private String buildEscalationReason(DisruptionAssessment assessment, boolean compensationRequested) {
        if (compensationRequested) {
            return "Solicitud de compensacion economica (R4) y asistencia prioritaria";
        }
        if (assessment.includesMinor() && assessment.overnightRequired()) {
            return "Grupo familiar con menor de edad (R5) y pernocta en conexion";
        }
        return "Revision humana requerida por politica operativa";
    }

    private String buildSanitizedEvidence(DisruptionAssessment assessment, String mode) {
        return "policy=" + assessment.policyVersion()
                + ";delayMin=" + assessment.primaryDelayMinutes()
                + ";missedConn=" + assessment.missedConnection()
                + ";minorInGroup=" + assessment.includesMinor()
                + ";mode=" + mode;
    }
}
