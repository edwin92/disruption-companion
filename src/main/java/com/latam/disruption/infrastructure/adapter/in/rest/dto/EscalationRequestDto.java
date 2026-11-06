package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscalationRequestDto(
        @NotBlank(message = "El motivo de escalamiento (reason) es obligatorio")
        @Size(min = 5, max = 300, message = "El motivo debe tener entre 5 y 300 caracteres")
        String reason
) {
}
