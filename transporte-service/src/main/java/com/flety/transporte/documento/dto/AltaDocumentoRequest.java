package com.flety.transporte.documento.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record AltaDocumentoRequest(
        @NotNull Long tipoDocumentoId,
        @NotBlank @Size(max = 255) String numero,
        @Future(message = "el documento ya está vencido") Instant vigencia,
        @Positive @Digits(integer = 12, fraction = 2) BigDecimal sumaAsegurada
) {
}
