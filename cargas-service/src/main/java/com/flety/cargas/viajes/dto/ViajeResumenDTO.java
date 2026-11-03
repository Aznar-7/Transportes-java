package com.flety.cargas.viajes.dto;

import com.flety.cargas.viajes.TipoEstadoViaje;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un renglón de GET /api/viajes: lo justo para elegir un viaje de la lista, sin contactos ni línea de tiempo.
 *
 * @param miRol con qué rol participa en este viaje quien consulta: DADOR o TRANSPORTISTA
 */
public record ViajeResumenDTO(
        Long id,
        Long cargaId,
        TipoEstadoViaje estado,
        String miRol,
        String origenLocalidad,
        String destinoLocalidad,
        Instant cargaDesde,
        Instant descargaHasta,
        BigDecimal precioAcordado
) {
}
