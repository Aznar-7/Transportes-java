package com.flety.commons.geo;

/**
 * Cuántos km de más hace el camión para levantar y entregar una carga en su vuelta.
 *
 * @param aproximado true si se calculó en línea recta porque la API de rutas no respondió
 */
public record ResultadoDesvio(double desvioKm, boolean aproximado) {
}
