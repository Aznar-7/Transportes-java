package com.flety.transporte.transportista.dto;

import com.flety.commons.validacion.CuitValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AltaTransportistaRequest(

        @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 dígitos sin guiones") @CuitValido String cuit,
        @NotBlank @Size(max = 255) String razonSocial,
        @NotBlank @Pattern(regexp = "\\+?\\d{8,15}", message = "debe tener entre 8 y 15 dígitos") String telefono
) {
}
