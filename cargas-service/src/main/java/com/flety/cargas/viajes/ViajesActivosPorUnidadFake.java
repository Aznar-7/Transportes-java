package com.flety.cargas.viajes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViajesActivosPorUnidad falso: devuelve los viajes que el test le cargue. Una unidad sin viajes devuelve lista vacía.
 *
 * <pre>
 * var viajes = new ViajesActivosPorUnidadFake()
 *         .conViaje(10L, new ViajeActivo(1L, 5L, desde, hasta));
 * </pre>
 */
public class ViajesActivosPorUnidadFake implements ViajesActivosPorUnidad {

    private final Map<Long, List<ViajeActivo>> viajesPorUnidad = new HashMap<>();

    public ViajesActivosPorUnidadFake conViaje(Long unidadId, ViajeActivo viaje) {
        viajesPorUnidad.computeIfAbsent(unidadId, id -> new ArrayList<>()).add(viaje);
        return this;
    }

    @Override
    public List<ViajeActivo> buscar(Long unidadId) {
        return List.copyOf(viajesPorUnidad.getOrDefault(unidadId, List.of()));
    }
}
