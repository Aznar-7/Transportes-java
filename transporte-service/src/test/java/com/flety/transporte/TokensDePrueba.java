package com.flety.transporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reemplaza el JwtDecoder real (que baja las claves de Keycloak) por uno que reconoce tokens fijos.
 * El resto de la cadena de seguridad de flety-commons es la real. Mismo enfoque que en cargas-service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TokensDePrueba {

    public static final String TRANSPORTISTA = "Bearer token-transportista1";
    public static final String OTRO_TRANSPORTISTA = "Bearer token-transportista2";
    public static final String OPERADOR = "Bearer token-operador1";

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> switch (token) {
            case "token-transportista1" -> jwt(token, "sub-transportista1", "TRANSPORTISTA");
            case "token-transportista2" -> jwt(token, "sub-transportista2", "TRANSPORTISTA");
            case "token-operador1" -> jwt(token, "sub-operador1", "OPERADOR");
            default -> throw new BadJwtException("Firma inválida");
        };
    }

    private static Jwt jwt(String token, String sub, String rol) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(sub)
                .claim("realm_access", Map.of("roles", List.of(rol)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
    }
}
