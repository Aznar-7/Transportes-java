package com.flety.cargas.viajes;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * La implementación real de ViajesActivosPorUnidad, sobre la tabla de viajes (FLY-25).
 * Mercado (FLY-23) la recibe inyectada por la interfaz y nunca ve Viaje ni ViajeRepository;
 * en sus tests sigue usando ViajesActivosPorUnidadFake.
 */
@Component
@Transactional(readOnly = true)
public class ViajesActivosPorUnidadJpa implements ViajesActivosPorUnidad {

    private final ViajeRepository viajes;

    public ViajesActivosPorUnidadJpa(ViajeRepository viajes) {
        this.viajes = viajes;
    }

    /** Activo es todo lo que no está CONFORMADO, CERRADO_CON_DISPUTA ni CANCELADO (TipoEstadoViaje.activo). */
    @Override
    public List<ViajeActivo> buscar(Long unidadId) {
        return viajes.buscarVentanas(unidadId, TipoEstadoViaje.activos());
    }
}
