package com.flety.cargas.transporte;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Propiedades flety.transporte.* para llegar a transporte-service.
 *
 * @param url             en Docker es http://transporte-service:8091 (nombre del contenedor en flety-net)
 * @param timeoutConexion cuánto esperar a que transporte acepte la conexión
 * @param timeoutLectura  cuánto esperar la respuesta; pasado ese tiempo se falla cerrado con 503
 */
@ConfigurationProperties("flety.transporte")
public record TransporteProperties(@DefaultValue("http://localhost:8091") String url,
                                   @DefaultValue("2s") Duration timeoutConexion,
                                   @DefaultValue("5s") Duration timeoutLectura) {
}
