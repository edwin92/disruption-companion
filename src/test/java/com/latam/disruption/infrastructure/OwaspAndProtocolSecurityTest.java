package com.latam.disruption.infrastructure;

import com.latam.disruption.infrastructure.util.LogSanitizer;
import com.latam.disruption.infrastructure.util.PiiMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwaspAndProtocolSecurityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("disruption_sec_db")
            .withUsername("latam_sec")
            .withPassword("latam_sec_pwd");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "AB12",
            "ABCDEFGHIJ123",
            "abc123",
            "ABC-12",
            "ABC_12",
            "' OR 1=1--",
            "<script>",
            "${7*7}",
            "ABC123;DROP"
    })
    void bookingIdInvalidoOConPayloadOwaspRetorna400(String maliciousBookingId) throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/{id}/evaluaciones", maliciousBookingId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "1234567",
            "key with spaces 12345",
            "key<script>alert(1)</script>",
            "key' OR '1'='1",
            "key;DROP TABLE rebooking_requests;--"
    })
    void idempotencyKeyInvalidaOConInyeccionRetorna400(String badKey) throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", badKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void jsonConPropiedadDesconocidaMassAssignmentRetorna400() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .header("Idempotency-Key", "idem-sec-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\",\"adminOverride\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_OR_UNKNOWN_PAYLOAD")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_OR_UNKNOWN_PAYLOAD")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void verboHttpNoPermitidoRetorna405() throws Exception {
        mockMvc.perform(delete("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/evaluaciones")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")))
                .andExpect(jsonPath("$.status", is(405)));

        mockMvc.perform(put("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/reubicaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"O1\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")))
                .andExpect(jsonPath("$.status", is(405)));
    }

    @Test
    void cabeceraAcceptNoSoportadaRetorna406() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/evaluaciones")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code", is("NOT_ACCEPTABLE_REPRESENTATION")))
                .andExpect(jsonPath("$.status", is(406)));
    }

    @Test
    void contentTypeNoSoportadoEnPostRetorna415() throws Exception {
        mockMvc.perform(post("/operaciones/vuelos/v1/disrupciones/reservas/ABC123/consultas")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("¿Que opciones tengo?"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")))
                .andExpect(jsonPath("$.status", is(415)));
    }

    @Test
    void rutaInexistenteRetorna404Controlado() throws Exception {
        mockMvc.perform(get("/operaciones/vuelos/v1/disrupciones/ruta-inexistente")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void logSanitizerNeutralizaSaltosDeLineaCwe117() {
        String malicious = "ABC123\r\nINFO Fake log entry injected\t<script>";
        String sanitized = LogSanitizer.sanitize(malicious);
        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\n"));
        assertEquals("null", LogSanitizer.sanitize(null));
    }

    @Test
    void piiMaskerOcultaIdentificadoresSensibles() {
        assertEquals("AB***3", PiiMasker.maskBookingOrToken("ABC123"));
        assertEquals("***", PiiMasker.maskBookingOrToken("AB"));
        assertEquals("***", PiiMasker.maskBookingOrToken(null));
    }
}
