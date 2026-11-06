package com.latam.disruption.domain.model;

public record FlightSegment(
        String segmentId,
        String flightNumber,
        String origin,
        String destination,
        String date,
        String scheduledDeparture,
        String scheduledArrival
) {
}
