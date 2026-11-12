package com.flety.commons.geo;

import java.util.List;
import java.util.Objects;

/**
 * Estrategia de cálculo de desvío basada en rutas reales con {@link RoutingClient} (FLY-43).
 * <p>
 * Calcula la distancia real de la ruta directa del retorno y la compara contra la ruta
 * que pasa por los puntos de retiro y entrega de la carga:
 * <pre>
 *   desvío = ruta(origen, retiro, entrega, destino) - ruta(origen, destino)
 * </pre>
 * El resultado se marca como exacto ({@code aproximado = false}).
 * </p>
 */
public class RutaOpenRouteService implements CalculadorDesvio {

    private final RoutingClient routingClient;

    public RutaOpenRouteService(RoutingClient routingClient) {
        this.routingClient = Objects.requireNonNull(routingClient, "El cliente de rutas no puede ser nulo");
    }

    @Override
    public ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        validarCoordenadas(origenRetorno, destinoRetorno, retiro, entrega);

        Ruta rutaDirecta = routingClient.calcularRuta(List.of(origenRetorno, destinoRetorno));
        Ruta rutaConDesvio = routingClient.calcularRuta(List.of(origenRetorno, retiro, entrega, destinoRetorno));

        double desvioKm = Math.max(0.0, rutaConDesvio.distanciaKm() - rutaDirecta.distanciaKm());
        return new ResultadoDesvio(desvioKm, false);
    }

    private void validarCoordenadas(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        if (origenRetorno == null || destinoRetorno == null || retiro == null || entrega == null) {
            throw new IllegalArgumentException("Las coordenadas para calcular el desvío no pueden ser nulas");
        }
    }
}

