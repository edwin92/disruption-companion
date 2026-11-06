package com.latam.disruption.domain.exception;

public abstract class DomainOrTechnicalBaseException extends RuntimeException {

    private final String code;

    protected DomainOrTechnicalBaseException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected DomainOrTechnicalBaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
