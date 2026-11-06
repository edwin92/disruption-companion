package com.latam.disruption.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RebookingRequestDto(
        @NotBlank(message = "El identificador de opcion (optionId) es obligatorio")
        @Pattern(regexp = "^O[0-9]{1,3}$", message = "El optionId debe cumplir el formato O1..O999")
        String optionId
) {
}
