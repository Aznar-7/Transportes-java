package com.flety.commons.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * Propiedades comunes de configuración HTTP de Flety (prefijo {@code flety.http}).
 *
 * @param hostsInternos lista de nombres de host considerados servicios internos
 *                      a los que está permitido reenviar el token JWT del usuario (FLY-35).
 */
@ConfigurationProperties("flety.http")
public record HttpProperties(
        @DefaultValue({"transporte-service", "cargas-service", "localhost"})
        Set<String> hostsInternos
) {
}

