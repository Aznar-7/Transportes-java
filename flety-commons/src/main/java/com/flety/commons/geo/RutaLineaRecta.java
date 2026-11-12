package com.flety.commons.geo;

import java.util.List;

/**
 * Estrategia de cálculo de desvío en línea recta basada en {@link GeoUtils} (FLY-43).
 * <p>
 * Se utiliza para calcular desvíos de manera rápida sin invocar servicios externos o como
 * estrategia de contingencia (fallback) ante caídas de la API de rutas.
 * </p>
 * <p>
 * Calcula la distancia directa del retorno y la compara contra la distancia acumulada
 * pasando por los puntos de retiro y entrega:
 * <pre>
 *   desvío = (distancia(origen, retiro) + distancia(retiro, entrega) + distancia(entrega, destino))
 *            - distancia(origen, destino)
 * </pre>
 * El resultado siempre se marca como aproximado ({@code aproximado = true}).
 * </p>
 */
public class RutaLineaRecta implements CalculadorDesvio {

    public RutaLineaRecta() {
    }

    @Override
    public ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        validarCoordenadas(origenRetorno, destinoRetorno, retiro, entrega);

        double distanciaDirecta = GeoUtils.distanciaEnKm(origenRetorno, destinoRetorno);
        double distanciaConDesvio = GeoUtils.distanciaEnKm(List.of(origenRetorno, retiro, entrega, destinoRetorno));

        double desvioKm = Math.max(0.0, distanciaConDesvio - distanciaDirecta);
        return new ResultadoDesvio(desvioKm, true);
    }

    private void validarCoordenadas(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        if (origenRetorno == null || destinoRetorno == null || retiro == null || entrega == null) {
            throw new IllegalArgumentException("Las coordenadas para calcular el desvío no pueden ser nulas");
        }
    }
}

