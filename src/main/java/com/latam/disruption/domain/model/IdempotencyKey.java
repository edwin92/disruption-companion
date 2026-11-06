package com.latam.disruption.domain.model;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;

import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    public IdempotencyKey {
        if (value == null || !VALID_PATTERN.matcher(value).matches()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_IDEMPOTENCY_KEY",
                    "La cabecera Idempotency-Key es obligatoria y debe tener entre 8 y 64 caracteres seguros"
            );
        }
    }
}
