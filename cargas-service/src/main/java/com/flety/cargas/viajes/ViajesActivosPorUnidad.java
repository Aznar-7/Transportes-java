package com.flety.cargas.viajes;

import java.time.Instant;
import java.util.List;

/**
 * Contrato entre viajes y mercado para RN-15: una unidad no puede tener dos viajes activos con ventanas superpuestas.
 * DsiGod la implementa en FLY-25 sobre la tabla de viajes; Heron la usa al crear una propuesta (FLY-23).
 * Así mercado no depende de la entidad Viaje ni de su repositorio, solo de esta interfaz.
 */
public interface ViajesActivosPorUnidad {

    /** Viajes de la unidad que no están cancelados ni cerrados. */
    List<ViajeActivo> buscar(Long unidadId);

    default boolean haySuperposicion(Long unidadId, Instant desde, Instant hasta) {
        return buscar(unidadId).stream().anyMatch(v -> v.seSuperponeCon(desde, hasta));
    }
}
