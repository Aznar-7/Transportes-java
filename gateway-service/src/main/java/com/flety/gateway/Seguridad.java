package com.flety.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Control grueso por rol (sección 9 del diseño y docs/gateway/rutas.md). De quién es el recurso lo decide cada servicio.
 * Spring Security aplica la PRIMERA regla que coincide: las específicas van antes que las generales.
 */
@Configuration
@EnableWebFluxSecurity
public class Seguridad {

    private static final String TRANSPORTISTA = "TRANSPORTISTA";
    private static final String DADOR = "DADOR";
    private static final String OPERADOR = "OPERADOR";

    @Bean
    SecurityWebFilterChain cadena(ServerHttpSecurity http) {
        JwtAuthenticationConverter roles = new JwtAuthenticationConverter();
        roles.setJwtGrantedAuthoritiesConverter(new RolesKeycloak());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(rutas -> rutas
                        // Sin ruta en el Gateway: se deja pasar para que responda 404 y no revele que existe.
                        .pathMatchers("/internal/**").permitAll()
                        .pathMatchers("/actuator/health").permitAll()

                        .pathMatchers("/api/transporte/operador/**").hasRole(OPERADOR)
                        .pathMatchers("/api/transporte/**").hasRole(TRANSPORTISTA)

                        .pathMatchers("/api/dadores/**").hasRole(DADOR)

                        .pathMatchers(HttpMethod.GET, "/api/cargas/de-paso").hasRole(TRANSPORTISTA)
                        .pathMatchers(HttpMethod.POST, "/api/cargas/*/propuestas").hasRole(TRANSPORTISTA)
                        .pathMatchers(HttpMethod.GET, "/api/cargas/*/propuestas").hasRole(DADOR)
                        .pathMatchers(HttpMethod.DELETE, "/api/cargas/propuestas/*").hasRole(TRANSPORTISTA)
                        .pathMatchers("/api/cargas/propuestas/*/aceptacion", "/api/cargas/propuestas/*/rechazo").hasRole(DADOR)
                        .pathMatchers(HttpMethod.GET, "/api/cargas/*").hasAnyRole(DADOR, TRANSPORTISTA)
                        .pathMatchers("/api/cargas", "/api/cargas/**").hasRole(DADOR)

                        .pathMatchers("/api/viajes/disputas/**").hasRole(OPERADOR)
                        .pathMatchers("/api/viajes/*/hitos").hasRole(TRANSPORTISTA)
                        .pathMatchers("/api/viajes/*/conformidad", "/api/viajes/*/disputas").hasRole(DADOR)
                        .pathMatchers("/api/viajes", "/api/viajes/**").hasAnyRole(DADOR, TRANSPORTISTA)

                        .anyExchange().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(roles))))
                .build();
    }
}
