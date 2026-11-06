package com.latam.disruption.infrastructure.adapter.out.tools;

import com.latam.disruption.application.port.out.EligibleOptionsPort;
import com.latam.disruption.application.port.out.HumanCaseGatewayPort;
import com.latam.disruption.application.port.out.OperationalEventsPort;
import com.latam.disruption.application.port.out.PassengerNotificationPort;
import com.latam.disruption.application.port.out.PolicyRulesPort;
import com.latam.disruption.application.port.out.RebookingGatewayPort;
import com.latam.disruption.application.port.out.TripContextPort;
import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.BookingId;
import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.FareConditions;
import com.latam.disruption.domain.model.FlightSegment;
import com.latam.disruption.domain.model.IdempotencyKey;
import com.latam.disruption.domain.model.OperationalEvent;
import com.latam.disruption.domain.model.OperationalEventsSnapshot;
import com.latam.disruption.domain.model.Passenger;
import com.latam.disruption.domain.model.PassengerNotificationReceipt;
import com.latam.disruption.domain.model.PolicyRule;
import com.latam.disruption.domain.model.PolicyRulesSnapshot;
import com.latam.disruption.domain.model.TripContext;
import com.latam.disruption.domain.model.UpstreamHumanCaseReceipt;
import com.latam.disruption.domain.model.UpstreamRebookingReceipt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OperationalToolsAdapter implements
        TripContextPort,
        OperationalEventsPort,
        PolicyRulesPort,
        EligibleOptionsPort,
        RebookingGatewayPort,
        HumanCaseGatewayPort,
        PassengerNotificationPort {

    private static final Set<String> KNOWN_BOOKINGS = Set.of(
            "ABC123", "TIM503", "EXP123", "ALLDEG", "CONFL1"
    );

    private final AtomicInteger rebookingSequence = new AtomicInteger(88230);
    private final AtomicInteger humanCaseSequence = new AtomicInteger(4470);
    private final AtomicInteger notificationSequence = new AtomicInteger(9911);

    public OperationalToolsAdapter() {
        super();
    }

    @Override
    public Optional<TripContext> getTripContext(BookingId bookingId) {
        String id = bookingId.value();
        if (!KNOWN_BOOKINGS.contains(id)) {
            return Optional.empty();
        }
        return Optional.of(new TripContext(
                id,
                List.of(
                        new Passenger(
                                "P1",
                                "María Silva",
                                "ADULT",
                                null,
                                "maria.silva@example.com",
                                "+56 9 8765 4321",
                                "12.345.678-9",
                                "GOLD"
                        ),
                        new Passenger(
                                "P2",
                                "Tomás Silva",
                                "CHILD",
                                7,
                                null,
                                null,
                                null,
                                null
                        )
                ),
                List.of(
                        new FlightSegment("S1", "LA500", "SCL", "LIM", "2026-09-15", "14:30", "17:55"),
                        new FlightSegment("S2", "LA2410", "LIM", "MEX", "2026-09-15", "19:05", "23:40")
                ),
                new FareConditions(true, false, "LIGHT", null),
                Instant.parse("2026-09-15T11:02:31Z"),
                "PARTIAL",
                List.of("fare_conditions.change_penalty_amount")
        ));
    }

    @Override
    public OperationalEventsSnapshot getOperationalEvents(String bookingId, List<String> segmentIds) {
        List<OperationalEvent> baseEvents = List.of(
                new OperationalEvent(
                        "E-1001", "S1", "DELAY", "17:10", 160, null, null,
                        "OPS_CORE", Instant.parse("2026-09-15T10:48:00Z")
                ),
                new OperationalEvent(
                        "E-1007", "S1", "GATE_CHANGE", null, 0, "24", null,
                        "AIRPORT_FEED", Instant.parse("2026-09-15T10:55:00Z")
                ),
                new OperationalEvent(
                        "E-1012", "S1", "STATUS", null, 0, null, "ON_TIME",
                        "LEGACY_MIRROR", Instant.parse("2026-09-15T09:30:00Z")
                ),
                new OperationalEvent(
                        "E-2003", "S2", "STATUS", null, 0, null, "SCHEDULED",
                        "OPS_CORE", Instant.parse("2026-09-15T10:48:00Z")
                )
        );

        if ("ALLDEG".equals(bookingId)) {
            return new OperationalEventsSnapshot(
                    baseEvents,
                    Instant.parse("2026-09-15T11:02:33Z"),
                    Set.of("OPS_CORE", "AIRPORT_FEED", "LEGACY_MIRROR"),
                    Set.of("OPS_CORE", "AIRPORT_FEED", "LEGACY_MIRROR")
            );
        }

        if ("CONFL1".equals(bookingId)) {
            return new OperationalEventsSnapshot(
                    baseEvents,
                    Instant.parse("2026-09-15T11:02:33Z"),
                    Set.of("OPS_CORE", "AIRPORT_FEED", "LEGACY_MIRROR"),
                    Set.of()
            );
        }

        return new OperationalEventsSnapshot(
                baseEvents,
                Instant.parse("2026-09-15T11:02:33Z"),
                Set.of("OPS_CORE", "AIRPORT_FEED", "LEGACY_MIRROR"),
                Set.of("LEGACY_MIRROR")
        );
    }

    @Override
    public PolicyRulesSnapshot getPolicyRules(String scenario) {
        return new PolicyRulesSnapshot(
                scenario != null ? scenario : "DELAY",
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

    @Override
    public EligibleOptionsSnapshot getEligibleOptions(TripContext tripContext) {
        List<CandidateOption> options = List.of(
                new CandidateOption(
                        "O1", "REBOOK", "LA2412", "LIM", "MEX",
                        "2026-09-16", "08:15", 0L, "CLP", 4, "POL-DELAY-2026-03", "HIGH"
                ),
                new CandidateOption(
                        "O2", "REBOOK", "LA2450", "LIM", "MEX",
                        "2026-09-15", "23:40", 180000L, "CLP", 1, "POL-DELAY-2026-03", "MEDIUM"
                ),
                new CandidateOption(
                        "O3", "REFUND", null, null, null,
                        null, null, 0L, "CLP", 0, "POL-REFUND-2026-01", "LOW"
                )
        );

        if ("EXP123".equals(tripContext.bookingId())) {
            return new EligibleOptionsSnapshot(
                    options,
                    Instant.parse("2026-09-15T10:00:00Z"),
                    10
            );
        }

        return new EligibleOptionsSnapshot(
                options,
                Instant.parse("2026-09-15T11:02:35Z"),
                120
        );
    }

    @Override
    public UpstreamRebookingReceipt createRebookingRequest(
            String bookingId,
            CandidateOption option,
            IdempotencyKey idempotencyKey
    ) {
        if ("TIM503".equals(bookingId) || idempotencyKey.value().startsWith("timeout-")) {
            throw new BusinessException(
                    TipoErrorNegocio.DEPENDENCIA_INDETERMINADA,
                    "UPSTREAM_TIMEOUT",
                    "Timeout al invocar create_rebooking_request; el resultado real es indeterminado"
            );
        }
        String requestId = "RBK-" + rebookingSequence.incrementAndGet();
        return new UpstreamRebookingReceipt(
                requestId,
                "PENDING_CONFIRMATION",
                Instant.parse("2026-09-15T11:12:00Z")
        );
    }

    @Override
    public UpstreamHumanCaseReceipt createHumanCase(
            String bookingId,
            String reason,
            String sanitizedEvidence
    ) {
        String caseId = "HC-" + humanCaseSequence.incrementAndGet();
        return new UpstreamHumanCaseReceipt(caseId, "DISRUPTION_L2", 45);
    }

    @Override
    public PassengerNotificationReceipt notifyPassenger(
            String bookingId,
            String message,
            String validatedPolicyRef
    ) {
        if (validatedPolicyRef == null || validatedPolicyRef.isBlank()) {
            throw new BusinessException(
                    TipoErrorNegocio.REGLA_POLITICA_INCUMPLIDA,
                    "UNVALIDATED_CONTENT",
                    "No se permite notificar al pasajero sin referencia de politica verificada"
            );
        }
        String notificationId = "NT-" + notificationSequence.incrementAndGet();
        return new PassengerNotificationReceipt(notificationId, "PUSH", "QUEUED");
    }
}
