package com.flety.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolesKeycloakTest {

    private final RolesKeycloak conversor = new RolesKeycloak();

    @Test
    void pasaLosRolesDelRealmAlFormatoDeSpring() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("DADOR", "offline_access"))));

        assertThat(conversor.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_DADOR", "ROLE_offline_access");
    }

    @Test
    void sinRealmAccessNoHayRoles() {
        assertThat(conversor.convert(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("sub")
                .claims(c -> c.putAll(claims)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
