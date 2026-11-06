package com.latam.disruption.infrastructure.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String codigoRespuesta,
        String message,
        int status,
        String path,
        Instant timestamp
) {
    public ApiErrorResponse(
            String code,
            String message,
            int status,
            String path,
            Instant timestamp
    ) {
        this(code, code, message, status, path, timestamp);
    }
}
