package com.flety.transporte.retorno.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/** Provincia y localidad, con las coordenadas opcionales: si no vienen, se las pide a Georef. */
public record LugarRequest(
        @NotBlank String provincia,
        @NotBlank String localidad,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng
) {

    @AssertTrue(message = "lat y lng van juntas o no van")
    public boolean isCoordenadasCompletas() {
        return (lat == null) == (lng == null);
    }

    public boolean tieneCoordenadas() {
        return lat != null;
    }
}
