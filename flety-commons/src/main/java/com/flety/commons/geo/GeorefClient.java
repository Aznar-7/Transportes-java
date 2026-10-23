package com.flety.commons.geo;

import com.flety.commons.excepciones.ServicioExternoException;

/**
 * Convierte provincia y localidad en coordenadas usando Georef Argentina.
 * La implementación real llega en FLY-36; hasta entonces se usa GeorefClientFake.
 */
public interface GeorefClient {

    /**
     * @throws ServicioExternoException si la localidad no existe o Georef no responde. Nunca inventa coordenadas.
     */
    Coordenada ubicar(String provincia, String localidad);
}
