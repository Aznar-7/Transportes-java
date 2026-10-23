package com.flety.commons.geo;

/**
 * Patrón Strategy para el desvío de una carga respecto de un retorno:
 * desvío = ruta(origen, retiro, entrega, destino) - ruta(origen, destino).
 * En FLY-43 se implementan RutaOpenRouteService (real) y RutaLineaRecta (aproximada); hasta entonces CalculadorDesvioFake.
 * Nunca lanza error por una caída de la API: en ese caso devuelve un resultado aproximado.
 */
public interface CalculadorDesvio {

    ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega);
}
