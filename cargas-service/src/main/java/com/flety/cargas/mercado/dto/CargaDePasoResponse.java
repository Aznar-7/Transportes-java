package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.Carga;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeoUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un resultado de la búsqueda: la vista reservada de la carga (RN-13) más lo que define su lugar en la lista (RN-02).
 *
 * @param desvioKm    km de más que hace el camión, redondeados a 100 m
 * @param precioPorKm precio ofrecido por km en línea recta entre el retiro y la entrega
 * @param aproximado  true si el desvío se calculó en línea recta porque la API de rutas no respondió
 */
public record CargaDePasoResponse(CargaReservadaResponse carga, double desvioKm, BigDecimal precioPorKm,
                                  boolean aproximado) {

    /** Si el retiro y la entrega casi coinciden, se divide por 1 km para que el precio por km no se dispare. */
    private static final double DISTANCIA_MINIMA_KM = 1;

    public static CargaDePasoResponse desde(Carga carga, double desvioKm, boolean aproximado) {
        return new CargaDePasoResponse(CargaReservadaResponse.desde(carga), Math.round(desvioKm * 10) / 10.0,
                precioPorKm(carga), aproximado);
    }

    private static BigDecimal precioPorKm(Carga carga) {
        double km = GeoUtils.distanciaEnKm(
                new Coordenada(carga.getOrigen().getLat(), carga.getOrigen().getLng()),
                new Coordenada(carga.getDestino().getLat(), carga.getDestino().getLng()));
        return carga.getPrecioOfrecido().divide(BigDecimal.valueOf(Math.max(km, DISTANCIA_MINIMA_KM)), 2, RoundingMode.HALF_UP);
    }
}
