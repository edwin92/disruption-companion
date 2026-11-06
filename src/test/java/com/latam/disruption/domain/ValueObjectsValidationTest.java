package com.latam.disruption.domain;

import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.BookingId;
import com.latam.disruption.domain.model.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueObjectsValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC123", "LA2026", "ZX987654"})
    void bookingIdValidoSeConstruyeCorrectamente(String raw) {
        BookingId id = new BookingId(raw);
        assertEquals(raw, id.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "AB12",
            "ABCDEFGHIJ123",
            "abc123",
            "ABC-123",
            "ABC%2F..%2F123",
            "../ABC123",
            "ABC/123",
            "' OR 1=1--",
            "<script>alert(1)</script>",
            "ABC123\r\nINFO",
            "${jndi:ldap://evil}",
            "{{7*7}}",
            "ABC\u0000123"
    })
    void bookingIdRechazaFormatosInvalidosEInyeccionesEnElBorde(String maliciousOrInvalid) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new BookingId(maliciousOrInvalid));
        assertEquals(TipoErrorNegocio.VALIDACION_ENTRADA, ex.getTipo());
    }

    @Test
    void bookingIdNuloEsRechazado() {
        BusinessException ex = assertThrows(BusinessException.class, () -> new BookingId(null));
        assertEquals(TipoErrorNegocio.VALIDACION_ENTRADA, ex.getTipo());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "idem-key-0001",
            "550e8400-e29b-41d4-a716-446655440000",
            "RBK_ABC123_20260915_01"
    })
    void idempotencyKeyValidaSeConstruyeCorrectamente(String raw) {
        IdempotencyKey key = new IdempotencyKey(raw);
        assertEquals(raw, key.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "short1",
            "a2345678901234567890123456789012345678901234567890123456789012345",
            "key con espacios",
            "key%2F..%2Fadmin",
            "key\r\nInjectedHeader: true",
            "key<script>alert(1)</script>",
            "key' OR '1'='1"
    })
    void idempotencyKeyRechazaValoresInvalidosOInyeccion(String invalidKey) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new IdempotencyKey(invalidKey));
        assertEquals(TipoErrorNegocio.VALIDACION_ENTRADA, ex.getTipo());
    }
}
