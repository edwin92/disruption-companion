package com.latam.disruption.domain.exception;

public class TechnicalException extends DomainOrTechnicalBaseException {

    public TechnicalException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
