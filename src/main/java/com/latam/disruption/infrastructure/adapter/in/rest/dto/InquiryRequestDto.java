package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryRequestDto(
        @NotBlank(message = "La pregunta del pasajero es obligatoria")
        @Size(min = 3, max = 500, message = "La pregunta debe tener entre 3 y 500 caracteres")
        String question
) {
}
