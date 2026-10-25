package com.flety.commons.seguridad;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un servicio de prueba que solo agrega flety-commons, con todo Spring levantado y la cadena de filtros real.
 * El único reemplazo es el JwtDecoder: en vez de bajar la clave pública de Keycloak, reconoce tres tokens fijos.
 * Así se prueba la conversión de roles de punta a punta sin Keycloak.
 */
class SeguridadAutoConfigurationTest {

    static final String TOKEN_TRANSPORTISTA = "token-transportista1";
    static final String TOKEN_DADOR = "token-dador1";

    @Nested
    @SpringBootTest(classes = ServicioDePrueba.class)
    @AutoConfigureMockMvc
    class ConSeguridadPrendida {

        @Autowired
        MockMvc mockMvc;

        @Test
        void sinTokenDevuelve401ComoProblemDetail() throws Exception {
            mockMvc.perform(get("/yo"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("No autenticado"));
        }

        @Test
        void conTokenInvalidoDevuelve401() throws Exception {
            mockMvc.perform(get("/yo").header(HttpHeaders.AUTHORIZATION, "Bearer token-adulterado"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("No autenticado"));
        }

        @Test
        void usuarioActualDevuelveSubYRolesDelToken() throws Exception {
            mockMvc.perform(get("/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TRANSPORTISTA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("3f1c-transportista1"))
                    .andExpect(jsonPath("$.roles", containsInAnyOrder("TRANSPORTISTA")));
        }

        @Test
        void conElRolQuePideElEndpointPasa() throws Exception {
            mockMvc.perform(get("/solo-dador").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_DADOR))
                    .andExpect(status().isOk());
        }

        @Test
        void conOtroRolDevuelve403ComoProblemDetail() throws Exception {
            mockMvc.perform(get("/solo-dador").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TRANSPORTISTA))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Sin permiso"));
        }
    }

    @Nested
    @SpringBootTest(classes = ServicioDePrueba.class, properties = "flety.seguridad.habilitada=false")
    @AutoConfigureMockMvc
    class ConSeguridadApagada {

        @Autowired
        MockMvc mockMvc;

        @Test
        void sinTokenPasaComoElUsuarioLocal() throws Exception {
            mockMvc.perform(get("/yo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("usuario-local"))
                    .andExpect(jsonPath("$.roles", containsInAnyOrder("TRANSPORTISTA", "DADOR", "OPERADOR")));
        }

        @Test
        void elUsuarioLocalPasaLosControlesDeRol() throws Exception {
            mockMvc.perform(get("/solo-dador")).andExpect(status().isOk());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ServicioDePrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case TOKEN_TRANSPORTISTA -> jwt(token, "3f1c-transportista1", "TRANSPORTISTA");
                case TOKEN_DADOR -> jwt(token, "9a2e-dador1", "DADOR");
                default -> throw new BadJwtException("Firma inválida");
            };
        }

        @Bean
        ControllerDePrueba controllerDePrueba(UsuarioActual usuarioActual) {
            return new ControllerDePrueba(usuarioActual);
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

    @RestController
    static class ControllerDePrueba {

        private final UsuarioActual usuarioActual;

        ControllerDePrueba(UsuarioActual usuarioActual) {
            this.usuarioActual = usuarioActual;
        }

        @GetMapping("/yo")
        Map<String, Object> yo() {
            return Map.of("id", usuarioActual.id(), "roles", usuarioActual.roles());
        }

        @GetMapping("/solo-dador")
        @PreAuthorize("hasRole('DADOR')")
        Set<String> soloDador() {
            return usuarioActual.roles();
        }
    }
}
