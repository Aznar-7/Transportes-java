package com.flety.transporte.retorno.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record PublicarRetornoRequest(
        @NotNull Long unidadId,
        @NotNull @Valid LugarRequest origen,
        @NotNull @Valid LugarRequest destino,
        @NotNull Instant salidaDesde,
        @NotNull @Future(message = "la ventana de salida ya pasó") Instant salidaHasta,
        @Positive Double desvioMaximoKm
) {

    /** Por defecto 30 km, como dice el diseño. */
    public double desvioMaximoKmOPorDefecto() {
        return desvioMaximoKm == null ? 30.0 : desvioMaximoKm;
    }

    @AssertTrue(message = "salidaHasta tiene que ser posterior a salidaDesde")
    public boolean isVentanaValida() {
        return salidaDesde == null || salidaHasta == null || salidaHasta.isAfter(salidaDesde);
    }
}
