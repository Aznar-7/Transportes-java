package com.flety.transporte.unidad.dto;

import com.flety.commons.contrato.TipoCarroceria;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AltaUnidadRequest(
        @NotNull @Pattern(regexp = "[A-Z]{3}\\d{3}|[A-Z]{2}\\d{3}[A-Z]{2}", message = "debe ser una patente como ABC123 o AB123CD") String dominio,
        @NotNull TipoCarroceria tipoCarroceria,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal capacidadToneladas,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal capacidadM3
) {
}
