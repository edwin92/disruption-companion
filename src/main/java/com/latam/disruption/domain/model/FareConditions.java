package com.latam.disruption.domain.model;

public record FareConditions(
        boolean changeable,
        boolean refundable,
        String fareFamily,
        Long changePenaltyAmount
) {
}
