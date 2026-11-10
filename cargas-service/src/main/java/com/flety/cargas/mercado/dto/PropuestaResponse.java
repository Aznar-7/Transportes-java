package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import com.flety.cargas.mercado.TipoPropuesta;

import java.math.BigDecimal;
import java.time.Instant;

public record PropuestaResponse(
        Long id,
        Long cargaId,
        Long transportistaId,
        Long unidadId,
        Long retornoId,
        TipoPropuesta tipo,
        BigDecimal precio,
        String mensaje,
        boolean requiereAceptacionRiesgo,
        Instant vencimiento,
        EstadoPropuesta estado,
        Instant creadaEn
) {

    public static PropuestaResponse desde(Propuesta p) {
        return new PropuestaResponse(p.getId(), p.getCarga().getId(), p.getTransportistaId(), p.getUnidadId(),
                p.getRetornoId(), p.getTipo(), p.getPrecio(), p.getMensaje(), p.isRequiereAceptacionRiesgo(),
                p.getVencimiento(), p.getEstado(), p.getCreadaEn());
    }
}
