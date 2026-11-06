package com.latam.disruption.domain.model;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;

import java.util.regex.Pattern;

public record BookingId(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Z0-9]{6,8}$");

    public BookingId {
        if (value == null || !VALID_PATTERN.matcher(value).matches()) {
            throw new BusinessException(
                    TipoErrorNegocio.VALIDACION_ENTRADA,
                    "INVALID_BOOKING_ID",
                    "El identificador de reserva debe contener entre 6 y 8 caracteres alfanumericos"
            );
        }
    }
}
