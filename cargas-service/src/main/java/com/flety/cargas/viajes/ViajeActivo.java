package com.flety.cargas.viajes;

import java.time.Instant;

/**
 * Lo mínimo de un viaje activo que el mercado necesita para controlar superposición (RN-15):
 * desde el inicio de la ventana de carga hasta el fin de la ventana de descarga.
 */
public record ViajeActivo(Long viajeId, Long cargaId, Instant desde, Instant hasta) {

    /** Dos intervalos se pisan si cada uno empieza antes de que termine el otro. */
    public boolean seSuperponeCon(Instant otroDesde, Instant otroHasta) {
        return desde.isBefore(otroHasta) && otroDesde.isBefore(hasta);
    }
}
