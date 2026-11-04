package com.flety.cargas.viajes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/viajes/{id}/cancelacion. El motivo es obligatorio: queda en la línea de tiempo y es lo que la otra
 * parte lee para entender por qué se cayó el acuerdo.
 */
public record PedidoCancelacionDTO(
        @NotBlank(message = "falta el motivo de la cancelación")
        @Size(max = 500, message = "el motivo tiene hasta 500 caracteres")
        String motivo
) {
}
