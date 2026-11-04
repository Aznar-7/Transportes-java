package com.flety.cargas.viajes;

import java.util.EnumMap;
import java.util.Map;

/**
 * Traduce el valor guardado en la base (TipoEstadoViaje) a su objeto de estado del patrón State.
 * <p>
 * En la base se guarda el enum, que es simple de consultar y de leer; el comportamiento vive en las clases.
 * Como los estados no tienen datos propios, hay una sola instancia de cada uno, compartida por todos los viajes.
 * <p>
 * Cada estado se registra con su propio tipo(): no puede quedar la clase de un estado anotada bajo otro.
 * Los tipos que todavía no tienen clase quedan con EstadoSinAcciones, así ningún valor del enum queda sin estado.
 */
final class EstadosViaje {

    private static final Map<TipoEstadoViaje, EstadoViaje> ESTADOS = new EnumMap<>(TipoEstadoViaje.class);

    static {
        registrar(new Confirmado());
        registrar(new Cargado());
        registrar(new EnTransito());
        registrar(new Entregado());
        for (TipoEstadoViaje tipo : TipoEstadoViaje.values()) {
            ESTADOS.putIfAbsent(tipo, new EstadoSinAcciones(tipo));
        }
    }

    private EstadosViaje() {
    }

    static EstadoViaje de(TipoEstadoViaje tipo) {
        return ESTADOS.get(tipo);
    }

    private static void registrar(EstadoViaje estado) {
        ESTADOS.put(estado.tipo(), estado);
    }
}
