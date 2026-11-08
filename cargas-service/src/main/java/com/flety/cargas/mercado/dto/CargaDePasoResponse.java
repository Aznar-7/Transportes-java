package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.Carga;

/**
 * Un resultado de la búsqueda: la vista reservada de la carga (RN-13) más cuánto se desvía el camión para llevarla.
 *
 * @param desvioKm   km de más que hace el camión, redondeados a 100 m
 * @param aproximado true si se calculó en línea recta porque la API de rutas no respondió
 */
public record CargaDePasoResponse(CargaReservadaResponse carga, double desvioKm, boolean aproximado) {

    public static CargaDePasoResponse desde(Carga carga, double desvioKm, boolean aproximado) {
        return new CargaDePasoResponse(CargaReservadaResponse.desde(carga), Math.round(desvioKm * 10) / 10.0, aproximado);
    }
}
