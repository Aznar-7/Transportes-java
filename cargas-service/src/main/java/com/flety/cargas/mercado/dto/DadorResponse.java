package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.Dador;

public record DadorResponse(Long id, String cuit, String razonSocial, String telefono, boolean declaraEmpresa) {

    public static DadorResponse desde(Dador d) {
        return new DadorResponse(d.getId(), d.getCuit(), d.getRazonSocial(), d.getTelefono(), d.isDeclaraEmpresa());
    }
}
