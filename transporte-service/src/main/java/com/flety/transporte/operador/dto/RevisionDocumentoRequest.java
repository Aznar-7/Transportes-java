package com.flety.transporte.operador.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RevisionDocumentoRequest(
        @NotNull Boolean aprobado,
        @Size(max = 255) String motivo
) {

    @AssertTrue(message = "un rechazo necesita motivo")
    public boolean isMotivoPresente() {
        return !Boolean.FALSE.equals(aprobado) || (motivo != null && !motivo.isBlank());
    }
}
