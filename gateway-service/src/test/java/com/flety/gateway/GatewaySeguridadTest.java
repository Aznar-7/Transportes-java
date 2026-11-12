package com.flety.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Las reglas por rol con la cadena de seguridad real. Un HttpServer del JDK hace de transporte y de cargas:
 * responde 200 con el servicio que lo atendió y el Authorization que le llegó, así se ve que el Gateway reenvía.
 * Solo el JwtDecoder es falso: reconoce tokens fijos en vez de bajar las claves de Keycloak.
 */
@SpringBootTest
@Import(GatewaySeguridadTest.TokensDePrueba.class)
class GatewaySeguridadTest {

    private static final String TRANSPORTISTA = "Bearer token-transportista";
    private static final String DADOR = "Bearer token-dador";
    private static final String OPERADOR = "Bearer token-operador";

    private static final HttpServer TRANSPORTE = levantarBackend("transporte");
    private static final HttpServer CARGAS = levantarBackend("cargas");

    @DynamicPropertySource
    static void urlsDeLosServicios(DynamicPropertyRegistry propiedades) {
        propiedades.add("TRANSPORTE_URL", () -> "http://localhost:" + TRANSPORTE.getAddress().getPort());
        propiedades.add("CARGAS_URL", () -> "http://localhost:" + CARGAS.getAddress().getPort());
    }

    @AfterAll
    static void apagarBackends() {
        TRANSPORTE.stop(0);
        CARGAS.stop(0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TokensDePrueba {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "token-transportista" -> Mono.just(jwt(token, "TRANSPORTISTA"));
                case "token-dador" -> Mono.just(jwt(token, "DADOR"));
                case "token-operador" -> Mono.just(jwt(token, "OPERADOR"));
                default -> Mono.error(new BadJwtException("Firma inválida"));
            };
        }

        private static Jwt jwt(String token, String rol) {
            return Jwt.withTokenValue(token).header("alg", "RS256").subject("sub-" + rol.toLowerCase())
                    .claim("realm_access", Map.of("roles", List.of(rol)))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        }
    }

    @Autowired
    ApplicationContext contexto;

    WebTestClient cliente;

    @BeforeEach
    void armarCliente() {
        cliente = WebTestClient.bindToApplicationContext(contexto).build();
    }

    @Test
    void sinTokenDevuelve401() {
        pedir(HttpMethod.GET, "/api/transporte/unidades", null).expectStatus().isUnauthorized();
    }

    @Test
    void conUnTokenAdulteradoDevuelve401() {
        pedir(HttpMethod.GET, "/api/transporte/unidades", "Bearer cualquiera").expectStatus().isUnauthorized();
    }

    @Test
    void elTransportistaLlegaATransporteConSuToken() {
        pedir(HttpMethod.GET, "/api/transporte/unidades", TRANSPORTISTA)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("transporte " + TRANSPORTISTA);
    }

    @Test
    void unDadorEnTransporteDevuelve403() {
        pedir(HttpMethod.GET, "/api/transporte/unidades", DADOR).expectStatus().isForbidden();
    }

    @Test
    void lasRutasDelOperadorSonSoloDelOperador() {
        pedir(HttpMethod.PATCH, "/api/transporte/operador/documentos/1/revision", OPERADOR).expectStatus().isOk();
        pedir(HttpMethod.PATCH, "/api/transporte/operador/documentos/1/revision", TRANSPORTISTA).expectStatus().isForbidden();
    }

    @Test
    void lasCargasDePasoSonDelTransportistaAunqueElRestoDeCargasSeaDelDador() {
        pedir(HttpMethod.GET, "/api/cargas/de-paso?retornoId=1", TRANSPORTISTA)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("cargas " + TRANSPORTISTA);
        pedir(HttpMethod.GET, "/api/cargas/de-paso?retornoId=1", DADOR).expectStatus().isForbidden();
    }

    @Test
    void publicarUnaCargaEsDelDador() {
        pedir(HttpMethod.POST, "/api/cargas", DADOR).expectStatus().isOk();
        pedir(HttpMethod.POST, "/api/cargas", TRANSPORTISTA).expectStatus().isForbidden();
    }

    @Test
    void verUnaCargaEsDeLosDosYProponerEsDelTransportista() {
        pedir(HttpMethod.GET, "/api/cargas/5", TRANSPORTISTA).expectStatus().isOk();
        pedir(HttpMethod.GET, "/api/cargas/5", DADOR).expectStatus().isOk();
        pedir(HttpMethod.POST, "/api/cargas/5/propuestas", TRANSPORTISTA).expectStatus().isOk();
        pedir(HttpMethod.POST, "/api/cargas/5/propuestas", DADOR).expectStatus().isForbidden();
        pedir(HttpMethod.POST, "/api/cargas/propuestas/7/aceptacion", DADOR).expectStatus().isOk();
        pedir(HttpMethod.POST, "/api/cargas/propuestas/7/aceptacion", TRANSPORTISTA).expectStatus().isForbidden();
    }

    @Test
    void losHitosSonDelTransportistaYLasDisputasDelOperador() {
        pedir(HttpMethod.POST, "/api/viajes/3/hitos", TRANSPORTISTA).expectStatus().isOk();
        pedir(HttpMethod.POST, "/api/viajes/3/hitos", DADOR).expectStatus().isForbidden();
        pedir(HttpMethod.GET, "/api/viajes/disputas", OPERADOR).expectStatus().isOk();
        pedir(HttpMethod.GET, "/api/viajes/disputas", DADOR).expectStatus().isForbidden();
    }

    @Test
    void losInternosNoSePublicanYDevuelven404() {
        pedir(HttpMethod.GET, "/internal/unidades/1/habilitacion?hasta=2026-11-05T21:00:00Z", TRANSPORTISTA)
                .expectStatus().isNotFound();
        pedir(HttpMethod.GET, "/internal/retornos/1", null).expectStatus().isNotFound();
    }

    @Test
    void elHealthEsPublico() {
        pedir(HttpMethod.GET, "/actuator/health", null).expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec pedir(HttpMethod metodo, String path, String token) {
        return cliente.method(metodo).uri(path)
                .headers(h -> {
                    if (token != null) {
                        h.set(HttpHeaders.AUTHORIZATION, token);
                    }
                })
                .exchange();
    }

    /** Un servicio falso en un puerto libre: a cualquier path responde "servicio Authorization". */
    private static HttpServer levantarBackend(String servicio) {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", intercambio -> {
                byte[] cuerpo = (servicio + " " + intercambio.getRequestHeaders().getFirst("Authorization"))
                        .getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
