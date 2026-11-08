package com.flety.cargas.mercado.dto;

import com.flety.commons.validacion.CuitValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El @Pattern se suma a @CuitValido porque el validador también acepta guiones: acá se guarda un solo formato,
 * para que el índice único detecte el mismo CUIT escrito de dos maneras.
 */
public record AltaDadorRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos sin guiones") @CuitValido String cuit,
        @NotBlank @Size(max = 255) String razonSocial,
        @NotBlank @Pattern(regexp = "\\+?\\d{8,15}", message = "debe tener entre 8 y 15 dígitos") String telefono,
        @NotNull Boolean declaraEmpresa
) {
}
