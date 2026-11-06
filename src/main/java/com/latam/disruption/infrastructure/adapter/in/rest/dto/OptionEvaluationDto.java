package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import java.util.List;

public record OptionEvaluationDto(
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
        String confidence,
        boolean eligible,
        List<String> rejectionReasons
) {
}
