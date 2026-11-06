package com.latam.disruption.infrastructure.exception;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TechnicalException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.infrastructure.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = mapStatus(ex.getTipo());
        LOG.warn("Error de negocio controlado code={} status={} path={}",
                LogSanitizer.sanitize(ex.getCode()),
                status.value(),
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(ex.getCode(), ex.getMessage(), status, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String fieldName = fieldError != null ? fieldError.getField() : "payload";
        String detail = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Datos de entrada invalidos";
        LOG.warn("Rechazo de validacion en campo={} path={}",
                LogSanitizer.sanitize(fieldName),
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "INVALID_REQUEST_FIELD",
                detail,
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            Exception ex,
            HttpServletRequest request
    ) {
        LOG.warn("Rechazo por restriccion de parametro en path={}",
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "INVALID_PARAMETER_CONSTRAINT",
                "Los parametros enviados incumplen el formato permitido",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        LOG.warn("Solicitud malformada o con campos no permitidos en path={}",
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "MALFORMED_OR_UNKNOWN_PAYLOAD",
                "El cuerpo JSON o las cabeceras de la solicitud son invalidos o contienen propiedades no permitidas",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            Exception ex,
            HttpServletRequest request
    ) {
        LOG.warn("Ruta no encontrada path={}", LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "RESOURCE_NOT_FOUND",
                "El recurso o ruta solicitada no existe",
                HttpStatus.NOT_FOUND,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        LOG.warn("Metodo HTTP no permitido method={} path={}",
                LogSanitizer.sanitize(ex.getMethod()),
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "METHOD_NOT_ALLOWED",
                "El verbo HTTP utilizado no esta soportado en este recurso",
                HttpStatus.METHOD_NOT_ALLOWED,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        LOG.warn("Representacion no aceptable en path={}",
                LogSanitizer.sanitize(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "NOT_ACCEPTABLE_REPRESENTATION",
                        "El formato solicitado en la cabecera Accept no es soportado",
                        HttpStatus.NOT_ACCEPTABLE.value(),
                        request.getRequestURI(),
                        Instant.now(clock)
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        LOG.warn("Content-Type no soportado en path={}",
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "UNSUPPORTED_MEDIA_TYPE",
                "El Content-Type enviado no es soportado; utilice application/json",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ApiErrorResponse> handleTechnicalException(
            TechnicalException ex,
            HttpServletRequest request
    ) {
        LOG.error("Fallo tecnico controlado code={} path={}",
                LogSanitizer.sanitize(ex.getCode()),
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                ex.getCode(),
                "Error de comunicacion con dependencia operativa",
                HttpStatus.SERVICE_UNAVAILABLE,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        LOG.error("Error inesperado procesando solicitud en path={}",
                LogSanitizer.sanitize(request.getRequestURI()));
        return buildResponse(
                "INTERNAL_SERVER_ERROR",
                "Ocurrio un error interno procesando la solicitud",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getRequestURI()
        );
    }

    private HttpStatus mapStatus(TipoErrorNegocio tipo) {
        return switch (tipo) {
            case VALIDACION_ENTRADA -> HttpStatus.BAD_REQUEST;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO_IDEMPOTENCIA -> HttpStatus.CONFLICT;
            case REGLA_POLITICA_INCUMPLIDA -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DEPENDENCIA_INDETERMINADA -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            String code,
            String message,
            HttpStatus status,
            String path
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                code,
                message,
                status.value(),
                path,
                Instant.now(clock)
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
