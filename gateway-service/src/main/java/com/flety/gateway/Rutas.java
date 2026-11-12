package com.flety.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A qué servicio va cada path. Se reenvía sin reescribir: los servicios exponen los mismos paths
 * (docs/gateway/rutas.md). El header Authorization viaja tal cual y cada servicio vuelve a validar el token.
 * /internal/** no tiene ruta: desde afuera responde 404.
 */
@Configuration
public class Rutas {

    @Bean
    RouteLocator rutasFlety(RouteLocatorBuilder rutas,
                            @Value("${TRANSPORTE_URL:http://localhost:8091}") String transporte,
                            @Value("${CARGAS_URL:http://localhost:8092}") String cargas) {
        return rutas.routes()
                .route("transporte", r -> r.path("/api/transporte/**").uri(transporte))
                .route("cargas", r -> r.path("/api/dadores/**", "/api/cargas/**", "/api/viajes/**").uri(cargas))
                .build();
    }
}
