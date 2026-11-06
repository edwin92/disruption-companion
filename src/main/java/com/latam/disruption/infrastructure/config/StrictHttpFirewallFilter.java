package com.latam.disruption.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StrictHttpFirewallFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawUri = request.getRequestURI();
        String lowerUri = rawUri != null ? rawUri.toLowerCase(Locale.ROOT) : "";
        if (lowerUri.contains(";")
                || lowerUri.contains("..")
                || lowerUri.contains("%2e")
                || lowerUri.contains("%2f")
                || lowerUri.contains("%5c")
                || lowerUri.contains("%00")
                || lowerUri.contains("%0d")
                || lowerUri.contains("%0a")) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String json = "{\"code\":\"MALICIOUS_URI_REJECTED\","
                    + "\"codigoRespuesta\":\"MALICIOUS_URI_REJECTED\","
                    + "\"message\":\"La URI contiene secuencias o parametros de matriz no permitidos\","
                    + "\"status\":400,"
                    + "\"path\":\"REDACTED_URI\","
                    + "\"timestamp\":\"" + Instant.now() + "\"}";
            response.getWriter().write(json);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
