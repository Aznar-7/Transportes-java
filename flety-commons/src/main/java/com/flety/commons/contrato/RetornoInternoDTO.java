package com.flety.commons.contrato;

import com.flety.commons.geo.Coordenada;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta de GET /internal/retornos/{id} (transporte-service a cargas-service).
 * Trae lo necesario para buscar cargas de paso (flujo A, RN-01): la ruta, la ventana de salida,
 * el desvío máximo y la carrocería y capacidad de la unidad.
 *
 * @param estado          PUBLICADO, VENCIDO o CANCELADO (el enum vive en transporte-service, acá viaja como texto)
 * @param rutaPuntos      puntos de la ruta calculada; si la ruta es aproximada son solo origen y destino
 * @param rutaAproximada  true si la API de rutas falló y se usó la línea recta
 * @param transportistaSub sub del JWT del transportista dueño: cargas-service lo compara con el token (RN-T1)
 */
public record RetornoInternoDTO(
        Long id,
        Long transportistaId,
        Long unidadId,
        String estado,
        UbicacionDTO origen,
        UbicacionDTO destino,
        Instant salidaDesde,
        Instant salidaHasta,
        double desvioMaximoKm,
        double rutaDistanciaKm,
        double rutaDuracionMin,
        List<Coordenada> rutaPuntos,
        boolean rutaAproximada,
        TipoCarroceria tipoCarroceria,
        BigDecimal capacidadToneladas,
        BigDecimal capacidadM3,
        String transportistaSub
) {
    public RetornoInternoDTO {
        rutaPuntos = rutaPuntos == null ? List.of() : List.copyOf(rutaPuntos);
    }
}
