package com.flety.transporte.transportista.dto;

import com.flety.transporte.transportista.EstadoTransportista;
import com.flety.transporte.transportista.Transportista;

public record TransportistaResponse(Long id, String cuit, String razonSocial, String telefono, EstadoTransportista estado) {

    public static TransportistaResponse desde(Transportista t) {
        return new TransportistaResponse(t.getId(), t.getCuit(), t.getRazonSocial(), t.getTelefono(), t.getEstado());
    }
}
