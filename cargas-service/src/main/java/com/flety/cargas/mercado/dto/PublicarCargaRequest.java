package com.flety.cargas.mercado.dto;

import com.flety.commons.contrato.TipoCarroceria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Lo que manda el dador para publicar. Acá se valida solo la forma del pedido (400).
 * Las declaraciones no llevan @NotNull a propósito: no mandarlas es no declarar, y eso lo responde el service
 * con un 422 (RN-08), porque el pedido está bien formado pero incumple una regla del negocio.
 */
public record PublicarCargaRequest(
        @NotNull @Valid UbicacionRequest origen,
        @NotNull @Valid UbicacionRequest destino,
        @NotNull Instant cargaDesde,
        @NotNull @Future Instant cargaHasta,
        @NotNull Instant descargaDesde,
        @NotNull Instant descargaHasta,
        @NotBlank @Size(max = 255) String rubro,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal pesoToneladas,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal volumenM3,
        @NotEmpty Set<@NotNull TipoCarroceria> carroceriasAceptadas,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal precioOfrecido,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal valorDeclarado,
        Boolean declaraNoPeligrosa,
        Boolean declaraNoSobredimensionada
) {

    /** Cada ventana abre antes de cerrar, y no se puede descargar antes de cargar. Si falta una fecha, avisa @NotNull. */
    @AssertTrue(message = "las ventanas no son coherentes: cada una abre antes de cerrar y la descarga va después de la carga")
    public boolean isVentanasCoherentes() {
        if (cargaDesde == null || cargaHasta == null || descargaDesde == null || descargaHasta == null) {
            return true;
        }
        return cargaDesde.isBefore(cargaHasta)
                && descargaDesde.isBefore(descargaHasta)
                && !descargaDesde.isBefore(cargaDesde)
                && !descargaHasta.isBefore(cargaHasta);
    }
}
