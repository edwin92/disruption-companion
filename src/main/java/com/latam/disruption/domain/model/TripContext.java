package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.List;

public record TripContext(
        String bookingId,
        List<Passenger> passengers,
        List<FlightSegment> segments,
        FareConditions fareConditions,
        Instant retrievedAt,
        String completeness,
        List<String> missingFields
) {
    public boolean includesMinor() {
        return passengers != null && passengers.stream().anyMatch(Passenger::isMinor);
    }

    public int passengerCount() {
        return passengers == null ? 0 : passengers.size();
    }
}
