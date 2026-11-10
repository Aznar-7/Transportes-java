package com.flety.commons.seguridad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("Con usuario autenticado con JWT, hacia un host externo no agrega cabecera Authorization")
    void conJwtEnContextoHaciaHostExternoNoAgregaAuthorization() {
        String urlExterna = "https://apis.datos.gob.ar/georef/api/localidades?nombre=Cordoba";
        Jwt jwt = jwt("token-secreto-123", "sub-transportista");
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockServer.expect(requestTo(urlExterna))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"localidades\": []}", MediaType.APPLICATION_JSON));

        restClient.get().uri(urlExterna).retrieve().toBodilessEntity();
        mockServer.verify();
    }

    @Test
    @DisplayName("Con usuario autenticado con JWT, hacia localhost reenvía cabecera Authorization")
    void conJwtEnContextoHaciaLocalhostReenviaAuthorization() {
        String urlLocalhost = "http://localhost:8080/internal/cargas/1";
        Jwt jwt = jwt("token-secreto-123", "sub-dador");
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockServer.expect(requestTo(urlLocalhost))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-secreto-123"))
                .andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        restClient.get().uri(urlLocalhost).retrieve().toBodilessEntity();
        mockServer.verify();
    }

    @Test
    @DisplayName("Con hosts internos configurados a medida, solo esos hosts reciben Authorization")
    void conHostsInternosPersonalizadosAplicaSoloAEllos() {
        var interceptorPersonalizado = new ReenvioTokenInterceptor(Set.of("mi-servicio-propio"));
        RestClient.Builder customBuilder = RestClient.builder().requestInterceptor(interceptorPersonalizado);
        MockRestServiceServer customServer = MockRestServiceServer.bindTo(customBuilder).build();
        RestClient customClient = customBuilder.build();

        Jwt jwt = jwt("token-secreto-123", "sub-usuario");
        var auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        customServer.expect(requestTo("http://mi-servicio-propio/api"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-secreto-123"))
                .andRespond(withSuccess());

        customServer.expect(requestTo("http://transporte-service:8091/api"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess());

        customClient.get().uri("http://mi-servicio-propio/api").retrieve().toBodilessEntity();
        customClient.get().uri("http://transporte-service:8091/api").retrieve().toBodilessEntity();
        customServer.verify();
    }

    @Test
    @DisplayName("El customizer de commons queda registrado en el contexto junto con el de Spring Boot")
    void customizerDeCommonsQuedaRegistradoJuntoConElDeSpringBoot() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HttpMessageConvertersAutoConfiguration.class,
                        RestClientAutoConfiguration.class,
                        SeguridadAutoConfiguration.class
                ))
                .withBean(JwtDecoder.class, () -> token -> null)
                .withBean("handlerExceptionResolver", HandlerExceptionResolver.class, () -> (request, response, handler, ex) -> null)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("reenvioTokenRestClientCustomizer");
                    assertThat(context).hasBean("httpMessageConvertersRestClientCustomizer");

                    Map<String, RestClientCustomizer> customizers = context.getBeansOfType(RestClientCustomizer.class);
                    assertThat(customizers).containsKeys("reenvioTokenRestClientCustomizer", "httpMessageConvertersRestClientCustomizer");
                });
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
