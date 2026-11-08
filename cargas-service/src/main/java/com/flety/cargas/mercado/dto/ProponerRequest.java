package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.TipoPropuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RN-03: quien acepta el precio no manda ninguno (se usa el del dador, así nadie lo puede cambiar por error);
 * quien hace una contraoferta tiene que mandar el suyo.
 */
public record ProponerRequest(
        @NotNull Long unidadId,
        @NotNull Long retornoId,
        @NotNull TipoPropuesta tipo,
        @Positive @Digits(integer = 12, fraction = 2) BigDecimal precio,
        @Size(max = 280) String mensaje
) {

    @AssertTrue(message = "una contraoferta lleva precio; aceptar el precio del dador, no")
    @Schema(hidden = true)
    public boolean isPrecioCoherente() {
        return tipo == null || (tipo == TipoPropuesta.CONTRAOFERTA) == (precio != null);
    }
}
