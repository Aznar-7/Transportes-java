package com.flety.cargas.viajes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/viajes/{id}/disputas: el reclamo del dador. Los dos campos son obligatorios porque es lo que lee el
 * operador para decidir, junto con el comprobante de la entrega y la línea de tiempo del viaje.
 *
 * @param motivo      corto, por ejemplo "Faltante" o "Mercadería dañada"
 * @param descripcion qué pasó, con el detalle que haga falta
 */
public record PedidoDisputaDTO(
        @NotBlank(message = "falta el motivo de la disputa")
        @Size(max = 100, message = "el motivo tiene hasta 100 caracteres")
        String motivo,
        @NotBlank(message = "falta la descripción de la disputa")
        @Size(max = 1000, message = "la descripción tiene hasta 1000 caracteres")
        String descripcion
) {
}
