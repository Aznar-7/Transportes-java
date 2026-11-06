package com.flety.commons.seguridad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReenvioTokenInterceptorTest {

    /** Endpoint interno real documentado en el sistema: cargas-service -> transporte-service */
    private static final String URL = "http://transporte-service:8091/internal/unidades/1/habilitacion";

    private RestClient restClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(new ReenvioTokenInterceptor());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Con usuario autenticado con JWT, reenvía Authorization: Bearer <token>")
    void conJwtEnContextoReenviaAuthorizationBearer() {
        Jwt jwt = jwt("token-secreto-123", "sub-transportista");
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-secreto-123"))
                .andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        restClient.get().uri(URL).retrieve().toBodilessEntity();
        mockServer.verify();
    }

    @Test
    @DisplayName("Si la petición ya traía cabecera Authorization explícita, no la sobrescribe")
    void siYaPoseeAuthorizationNoLaSobrescribe() {
        Jwt jwt = jwt("token-contexto", "sub-dador");
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-manual-previo"))
                .andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        restClient.get()
                .uri(URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-manual-previo")
                .retrieve()
                .toBodilessEntity();

        mockServer.verify();
    }

    @Test
    @DisplayName("Sin usuario autenticado en SecurityContext, no agrega cabecera Authorization")
    void sinUsuarioNoAgregaAuthorization() {
        SecurityContextHolder.clearContext();

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        restClient.get().uri(URL).retrieve().toBodilessEntity();
        mockServer.verify();
    }

    @Test
    @DisplayName("Con usuario autenticado sin token JWT, continúa sin cabecera Authorization")
    void conAutenticacionSinTokenNoAgregaAuthorization() {
        var auth = UsernamePasswordAuthenticationToken.authenticated("usuario-local", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        restClient.get().uri(URL).retrieve().toBodilessEntity();
        mockServer.verify();
    }

    private static Jwt jwt(String tokenValue, String sub) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(sub)
                .claim("realm_access", Map.of("roles", List.of("TRANSPORTISTA")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
