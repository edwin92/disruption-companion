package com.latam.disruption.infrastructure.util;

public final class PiiMasker {

    private PiiMasker() {
        super();
    }

    public static String maskBookingOrToken(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 3) {
            return "***";
        }
        return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 1);
    }
}
