package com.flety.transporte.retorno.dto;

import com.flety.commons.contrato.UbicacionDTO;
import com.flety.commons.geo.Coordenada;
import com.flety.transporte.retorno.EstadoRetorno;
import com.flety.transporte.retorno.Retorno;

import java.time.Instant;
import java.util.List;

public record RetornoResponse(Long id, Long unidadId, EstadoRetorno estado, UbicacionDTO origen, UbicacionDTO destino,
                              Instant salidaDesde, Instant salidaHasta, double desvioMaximoKm,
                              double rutaDistanciaKm, double rutaDuracionMin, List<Coordenada> rutaPuntos,
                              boolean rutaAproximada) {

    public static RetornoResponse desde(Retorno r) {
        return new RetornoResponse(r.getId(), r.getUnidad().getId(), r.getEstado(), r.getOrigen().comoDTO(),
                r.getDestino().comoDTO(), r.getSalidaDesde(), r.getSalidaHasta(), r.getDesvioMaximoKm(),
                r.getRutaDistanciaKm(), r.getRutaDuracionMin(), r.getRutaPuntos(), r.isRutaAproximada());
    }
}
