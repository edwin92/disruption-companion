package com.latam.disruption.infrastructure.adapter.in.rest;

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
import com.latam.disruption.domain.model.DisruptionAssessment;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.AssessmentResponseDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.EscalationRequestDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.EscalationResponseDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.InquiryRequestDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.InquiryResponseDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.OptionEvaluationDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.RebookingRequestDto;
import com.latam.disruption.infrastructure.adapter.in.rest.dto.RebookingResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class DisruptionRestController {

    private static final String BASE_PATH = "/operaciones/vuelos/v1/disrupciones/reservas/{bookingId}";

    private final AssessTripDisruptionUseCase assessTripUseCase;
    private final ResolvePassengerInquiryUseCase resolveInquiryUseCase;
    private final CreateRebookingUseCase createRebookingUseCase;
    private final CreateHumanEscalationUseCase createHumanEscalationUseCase;

    public DisruptionRestController(
            AssessTripDisruptionUseCase assessTripUseCase,
            ResolvePassengerInquiryUseCase resolveInquiryUseCase,
            CreateRebookingUseCase createRebookingUseCase,
            CreateHumanEscalationUseCase createHumanEscalationUseCase
    ) {
        this.assessTripUseCase = assessTripUseCase;
        this.resolveInquiryUseCase = resolveInquiryUseCase;
        this.createRebookingUseCase = createRebookingUseCase;
        this.createHumanEscalationUseCase = createHumanEscalationUseCase;
    }

    @GetMapping(
            value = BASE_PATH + "/evaluaciones",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AssessmentResponseDto> evaluateDisruption(
            @PathVariable("bookingId")
            @Pattern(regexp = "^[A-Z0-9]{6,8}$", message = "bookingId debe ser alfanumerico de 6 a 8 caracteres")
            String bookingId
    ) {
        DisruptionAssessment assessment = assessTripUseCase.assessTrip(bookingId);
        List<OptionEvaluationDto> optionDtos = assessment.evaluatedOptions().stream()
                .map(ov -> new OptionEvaluationDto(
                        ov.option().optionId(),
                        ov.option().type(),
                        ov.option().flightNumber(),
                        ov.option().origin(),
                        ov.option().destination(),
                        ov.option().date(),
                        ov.option().departure(),
                        ov.option().fareDifference(),
                        ov.option().currency(),
                        ov.option().seatsAvailable(),
                        ov.option().policyRef(),
                        ov.option().confidence(),
                        ov.eligible(),
                        ov.rejectionReasons()
                ))
                .toList();

        String recommendedId = assessment.recommendedOption() != null
                ? assessment.recommendedOption().optionId()
                : null;
        String recommendedFlight = assessment.recommendedOption() != null
                ? assessment.recommendedOption().flightNumber()
                : null;

        AssessmentResponseDto response = new AssessmentResponseDto(
                assessment.bookingId(),
                assessment.passengerCount(),
                assessment.includesMinor(),
                assessment.relevantModification(),
                assessment.missedConnection(),
                assessment.primaryDelayMinutes(),
                assessment.updatedDepartureTime(),
                assessment.updatedGate(),
                assessment.sourceConflictDetected(),
                assessment.discardedSources(),
                assessment.policyVersion(),
                assessment.appliedRuleIds(),
                assessment.entitlements(),
                assessment.requiresHumanApproval(),
                assessment.overnightRequired(),
                assessment.blockedByMissingPenaltyAmount(),
                assessment.optionsExpired(),
                recommendedId,
                recommendedFlight,
                optionDtos,
                assessment.passengerExplanation()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = BASE_PATH + "/consultas",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<InquiryResponseDto> resolvePassengerInquiry(
            @PathVariable("bookingId")
            @Pattern(regexp = "^[A-Z0-9]{6,8}$", message = "bookingId debe ser alfanumerico de 6 a 8 caracteres")
            String bookingId,
            @Valid @RequestBody InquiryRequestDto request
    ) {
        InquiryResult result = resolveInquiryUseCase.resolveInquiry(
                new InquiryCommand(bookingId, request.question())
        );
        InquiryResponseDto response = new InquiryResponseDto(
                result.bookingId(),
                result.resolutionMode(),
                result.validatedPolicyRef(),
                result.recommendedOptionId(),
                result.confidenceScore(),
                result.requiresHumanEscalation(),
                result.humanCaseId(),
                result.guardrailRejectionReason(),
                result.message()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = BASE_PATH + "/reubicaciones",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RebookingResponseDto> createRebooking(
            @PathVariable("bookingId")
            @Pattern(regexp = "^[A-Z0-9]{6,8}$", message = "bookingId debe ser alfanumerico de 6 a 8 caracteres")
            String bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RebookingRequestDto request
    ) {
        RebookingResult result = createRebookingUseCase.createRebooking(
                new RebookingCommand(bookingId, idempotencyKey, request.optionId())
        );
        RebookingResponseDto response = new RebookingResponseDto(
                result.requestId(),
                result.bookingId(),
                result.optionId(),
                result.flightNumber(),
                result.status(),
                result.policyRef(),
                result.notificationId(),
                result.expiresAt(),
                result.replayedIdempotent()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            value = BASE_PATH + "/escalamientos",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EscalationResponseDto> createHumanEscalation(
            @PathVariable("bookingId")
            @Pattern(regexp = "^[A-Z0-9]{6,8}$", message = "bookingId debe ser alfanumerico de 6 a 8 caracteres")
            String bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EscalationRequestDto request
    ) {
        EscalationResult result = createHumanEscalationUseCase.createHumanEscalation(
                new EscalationCommand(bookingId, idempotencyKey, request.reason())
        );
        EscalationResponseDto response = new EscalationResponseDto(
                result.caseId(),
                result.bookingId(),
                result.queue(),
                result.slaMinutes(),
                result.reason(),
                result.replayedIdempotent()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
