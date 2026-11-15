package com.flety.cargas.viajes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/viajes/disputas/{id}/resolucion: cómo resuelve el operador. La resolución es obligatoria porque queda en
 * la línea de tiempo del viaje y es lo que leen las dos partes para entender la decisión.
 *
 * @param aFavorDelTransportista true: el viaje queda CONFORMADO; false: queda CERRADO_CON_DISPUTA
 */
public record PedidoResolucionDTO(
        @NotNull(message = "falta indicar si la resolución es a favor del transportista")
        Boolean aFavorDelTransportista,
        @NotBlank(message = "falta el fundamento de la resolución")
        @Size(max = 1000, message = "la resolución tiene hasta 1000 caracteres")
        String resolucion
) {
}
