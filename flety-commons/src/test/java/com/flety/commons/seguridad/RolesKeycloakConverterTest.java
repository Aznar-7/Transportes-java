package com.flety.commons.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El conversor con JWT de prueba armados a mano, con la misma forma que los que emite el realm flety.
 */
class RolesKeycloakConverterTest {

    private final RolesKeycloakConverter converter = new RolesKeycloakConverter();

    @Test
    void losRolesDeRealmAccessLleganComoRole() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("TRANSPORTISTA"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TRANSPORTISTA");
    }

    @Test
    void convierteTodosLosRolesDelToken() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("DADOR", "OPERADOR"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_DADOR", "ROLE_OPERADOR");
    }

    @Test
    void sinRealmAccessNoHayRoles() {
        assertThat(converter.convert(jwt(Map.of("scope", "openid profile")))).isEmpty();
    }

    @Test
    void conRealmAccessMalFormadoNoHayRoles() {
        assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "DADOR"))))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-de-prueba")
                .header("alg", "RS256")
                .subject("3f1c-transportista1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
