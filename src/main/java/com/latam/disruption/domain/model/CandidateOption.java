package com.latam.disruption.domain.model;

public record CandidateOption(
        String optionId,
        String type,
        String flightNumber,
        String origin,
        String destination,
        String date,
        String departure,
        Long fareDifference,
        String currency,
        int seatsAvailable,
        String policyRef,
        String confidence
) {
}
