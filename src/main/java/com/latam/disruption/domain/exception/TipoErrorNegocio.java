package com.latam.disruption.domain.exception;

public enum TipoErrorNegocio {
    VALIDACION_ENTRADA,
    NO_ENCONTRADO,
    CONFLICTO_IDEMPOTENCIA,
    REGLA_POLITICA_INCUMPLIDA,
    DEPENDENCIA_INDETERMINADA
}
