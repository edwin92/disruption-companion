package com.latam.disruption.domain.model;

public record Passenger(
        String passengerId,
        String name,
        String type,
        Integer age,
        String email,
        String phone,
        String documentId,
        String loyaltyTier
) {
    public boolean isMinor() {
        return "CHILD".equalsIgnoreCase(type) || (age != null && age < 18);
    }
}
