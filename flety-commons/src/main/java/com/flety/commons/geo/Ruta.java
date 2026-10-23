package com.flety.commons.geo;

import java.util.List;

/**
 * Una ruta calculada por la API de rutas: cuánto mide, cuánto se tarda y por dónde pasa.
 */
public record Ruta(double distanciaKm, double duracionMin, List<Coordenada> puntos) {

    public Ruta {
        puntos = puntos == null ? List.of() : List.copyOf(puntos);
    }
}
