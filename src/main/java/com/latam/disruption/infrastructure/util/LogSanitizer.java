package com.latam.disruption.infrastructure.util;

import org.owasp.encoder.Encode;

public final class LogSanitizer {

    private LogSanitizer() {
        super();
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        String cleaned = input.replace('\r', '_').replace('\n', '_').replace('\t', '_');
        String encoded = Encode.forJava(cleaned);
        if (encoded.length() > 120) {
            return encoded.substring(0, 120) + "...";
        }
        return encoded;
    }
}
