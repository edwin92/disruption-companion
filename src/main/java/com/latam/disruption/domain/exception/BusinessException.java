package com.latam.disruption.domain.exception;

public class BusinessException extends DomainOrTechnicalBaseException {

    private final TipoErrorNegocio tipo;

    public BusinessException(TipoErrorNegocio tipo, String code, String message) {
        super(code, message);
        this.tipo = tipo;
    }

    public TipoErrorNegocio getTipo() {
        return tipo;
    }
}
