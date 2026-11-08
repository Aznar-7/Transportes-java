package com.flety.cargas.mercado.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Origen o destino de una carga. Las coordenadas son opcionales: si no vienen, se le piden a Georef.
 */
public record UbicacionRequest(
        @NotBlank @Size(max = 255) String provincia,
        @NotBlank @Size(max = 255) String localidad,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotBlank @Size(max = 255) String direccion
) {

    /** Una sola coordenada no sirve para ubicar nada: vienen las dos o ninguna. */
    @AssertTrue(message = "lat y lng van juntas: mandá las dos o ninguna")
    @Schema(hidden = true)
    public boolean isCoordenadasCompletas() {
        return (lat == null) == (lng == null);
    }

    public boolean tieneCoordenadas() {
        return lat != null && lng != null;
    }
}
