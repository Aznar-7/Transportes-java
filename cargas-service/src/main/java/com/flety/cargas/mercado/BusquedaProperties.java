package com.flety.cargas.mercado;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades flety.busqueda.* de la búsqueda de cargas de paso.
 *
 * @param maxDesviosReales a cuántas cargas se les calcula el desvío real: cada una gasta llamadas a la API de rutas,
 *                         que tiene un cupo por minuto
 */
@ConfigurationProperties("flety.busqueda")
public record BusquedaProperties(@DefaultValue("10") int maxDesviosReales) {
}
