package com.flety.commons.geo;

import com.flety.commons.excepciones.ServicioExternoException;

import java.util.List;

/**
 * Calcula rutas por ruta real con la API de rutas (OpenRouteService).
 * La implementación real llega en FLY-37; hasta entonces se usa RoutingClientFake.
 */
public interface RoutingClient {

    /**
     * Ruta que pasa por los puntos en el orden dado (por ejemplo origen, retiro, entrega, destino).
     *
     * @throws ServicioExternoException si la API se cae, tarda demasiado o responde sin ruta.
     */
    Ruta calcularRuta(List<Coordenada> puntos);
}
