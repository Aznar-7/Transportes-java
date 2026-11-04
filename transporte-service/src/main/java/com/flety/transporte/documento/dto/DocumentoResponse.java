package com.flety.transporte.documento.dto;

import com.flety.transporte.documento.Documento;
import com.flety.transporte.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.Instant;

public record DocumentoResponse(Long id, Long unidadId, String tipo, String numero, Instant vigencia,
                                BigDecimal sumaAsegurada, EstadoDocumento estado, String motivoRechazo) {

    public static DocumentoResponse desde(Documento d) {
        return new DocumentoResponse(d.getId(), d.getUnidad().getId(), d.getTipo().getNombre(), d.getNumero(),
                d.getVigencia(), d.getSumaAsegurada(), d.getEstado(), d.getMotivoRechazo());
    }
}
