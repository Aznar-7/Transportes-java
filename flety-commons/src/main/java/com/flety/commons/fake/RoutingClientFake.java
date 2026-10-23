package com.flety.commons.fake;

import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.Ruta;
import com.flety.commons.geo.RoutingClient;

import java.util.List;

/**
 * RoutingClient falso: siempre devuelve la misma distancia y duración, y como puntos los mismos que recibió.
 * Con simularCaida(true) se comporta como la API de rutas caída (sirve para probar la ruta aproximada de FLY-12).
 */
public class RoutingClientFake implements RoutingClient {

    private double distanciaKm = 100.0;
    private double duracionMin = 90.0;
    private boolean caido;

    public RoutingClientFake conRuta(double distanciaKm, double duracionMin) {
        this.distanciaKm = distanciaKm;
        this.duracionMin = duracionMin;
        return this;
    }

    public RoutingClientFake simularCaida(boolean caido) {
        this.caido = caido;
        return this;
    }

    @Override
    public Ruta calcularRuta(List<Coordenada> puntos) {
        if (caido) {
            throw new ServicioExternoException("La API de rutas no responde (fake)");
        }
        if (puntos == null || puntos.size() < 2) {
            throw new IllegalArgumentException("Una ruta necesita al menos dos puntos");
        }
        return new Ruta(distanciaKm, duracionMin, puntos);
    }
}
