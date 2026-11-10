package com.flety.commons.geo;

import com.flety.commons.excepciones.ServicioExternoException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeorefClientHttpTest {

    private MockRestServiceServer mockServer;
    private GeorefClientHttp georefClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.datos.gob.ar/georef/api");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        georefClient = new GeorefClientHttp(builder.build());
    }

    @Test
    void localidadExistenteRetornaCoordenada() {
        String jsonRespuesta = """
                {
                  "cantidad": 1,
                  "total": 1,
                  "localidades": [
                    {
                      "id": "14014010000",
                      "nombre": "CORDOBA",
                      "centroide": {
                        "lat": -31.4201,
                        "lon": -64.1888
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(startsWith("https://apis.datos.gob.ar/georef/api/localidades")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("nombre", "C%C3%B3rdoba"))
                .andExpect(queryParam("provincia", "C%C3%B3rdoba"))
                .andExpect(queryParam("campos", "centroide"))
                .andExpect(queryParam("max", "1"))
                .andRespond(withSuccess(jsonRespuesta, MediaType.APPLICATION_JSON));

        Coordenada coordenada = georefClient.ubicar("Córdoba", "Córdoba");

        assertNotNull(coordenada);
        assertEquals(-31.4201, coordenada.lat());
        assertEquals(-64.1888, coordenada.lng());
        mockServer.verify();
    }

    @Test
    void localidadInexistenteLanzaServicioExternoException() {
        String jsonVacio = """
                {
                  "cantidad": 0,
                  "total": 0,
                  "localidades": []
                }
                """;

        mockServer.expect(requestTo(startsWith("https://apis.datos.gob.ar/georef/api/localidades")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("nombre", "PuebloFantasma"))
                .andExpect(queryParam("provincia", "C%C3%B3rdoba"))
                .andRespond(withSuccess(jsonVacio, MediaType.APPLICATION_JSON));

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> georefClient.ubicar("Córdoba", "PuebloFantasma")
        );

        assertTrue(ex.getMessage().contains("No pudimos ubicar la localidad PuebloFantasma, Córdoba"));
        mockServer.verify();
    }

    @Test
    void georefCaidoResponde500LanzaServicioExternoException() {
        mockServer.expect(requestTo(startsWith("https://apis.datos.gob.ar/georef/api/localidades")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> georefClient.ubicar("Córdoba", "Córdoba")
        );

        assertTrue(ex.getMessage().contains("Georef no responde"));
        mockServer.verify();
    }

    @Test
    void georefConErrorDeRedLanzaServicioExternoException() {
        mockServer.expect(requestTo(startsWith("https://apis.datos.gob.ar/georef/api/localidades")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("Connection reset by peer")));

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> georefClient.ubicar("Córdoba", "Córdoba")
        );

        assertTrue(ex.getMessage().contains("Georef no responde"));
        mockServer.verify();
    }

    @Test
    void parametrosVaciosLanzaServicioExternoExceptionSinLlamarAlServicio() {
        assertThrows(ServicioExternoException.class, () -> georefClient.ubicar(null, "Córdoba"));
        assertThrows(ServicioExternoException.class, () -> georefClient.ubicar("Córdoba", ""));
    }

    @Test
    void ubicarNoEnviaAuthorizationConUsuarioAutenticadoEnSecurityContext() {
        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none")
                .claim("sub", "usuario-123")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        try {
            String jsonRespuesta = """
                    {
                      "cantidad": 1,
                      "total": 1,
                      "localidades": [
                        {
                          "id": "14014010000",
                          "nombre": "CORDOBA",
                          "centroide": {
                            "lat": -31.4201,
                            "lon": -64.1888
                          }
                        }
                      ]
                    }
                    """;

            mockServer.expect(requestTo(startsWith("https://apis.datos.gob.ar/georef/api/localidades")))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess(jsonRespuesta, MediaType.APPLICATION_JSON));

            Coordenada coordenada = georefClient.ubicar("Córdoba", "Córdoba");

            assertNotNull(coordenada);
            mockServer.verify();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void clienteRealNoEnviaEncabezadoAuthorizationAlServidor() throws IOException {
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String jsonRespuesta = """
                {
                  "cantidad": 1,
                  "total": 1,
                  "localidades": [
                    {
                      "id": "14014010000",
                      "nombre": "CORDOBA",
                      "centroide": {
                        "lat": -31.4201,
                        "lon": -64.1888
                      }
                    }
                  ]
                }
                """;
        server.createContext("/localidades", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] response = jsonRespuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "none")
                .claim("sub", "usuario-123")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            GeorefClientHttp client = new GeorefClientHttp(baseUrl);

            Coordenada coordenada = client.ubicar("Córdoba", "Córdoba");

            assertNotNull(coordenada);
            assertNull(authHeader.get(), "GeorefClientHttp no debe enviar el encabezado Authorization");
        } finally {
            SecurityContextHolder.clearContext();
            server.stop(0);
        }
    }

    @Test
    void servidorLentoSuperaTimeoutLecturaYLanzaServicioExternoExceptionEnCincoSegundos() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/localidades", exchange -> {
            try {
                // Simula un servidor que tarda 8 segundos en responder (como en el escenario descripto)
                Thread.sleep(8000);
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Cliente cerró la conexión al cumplirse el timeout de lectura
            }
        });
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            GeorefClientHttp client = new GeorefClientHttp(baseUrl);

            long inicio = System.currentTimeMillis();
            ServicioExternoException ex = assertThrows(
                    ServicioExternoException.class,
                    () -> client.ubicar("Córdoba", "Córdoba")
            );
            long duracionMs = System.currentTimeMillis() - inicio;

            assertTrue(ex.getMessage().contains("Georef no responde"),
                    "El mensaje debería indicar que Georef no responde");
            // El servidor tardaba 8s, pero el timeout de lectura de 5s debe cortar antes (entre 4.5s y 7.5s)
            assertTrue(duracionMs >= 4500, "Debería esperar al menos el timeout de lectura (~5s), tardó: " + duracionMs + " ms");
            assertTrue(duracionMs < 7500, "Debería fallar antes de los 8s del servidor por el timeout de lectura (~5s), tardó: " + duracionMs + " ms");
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
