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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenRouteServiceClientHttpTest {

    private static final String API_KEY = "test-api-key-ors";
    private static final String BASE_URL = "https://api.openrouteservice.org";

    private MockRestServiceServer mockServer;
    private OpenRouteServiceClientHttp client;

    private static final String JSON_GEOJSON_VALIDO = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-64.1888, -31.4201],
                      [-63.2402, -32.4075],
                      [-60.6393, -32.9468]
                    ]
                  },
                  "properties": {
                    "summary": {
                      "distance": 401500.0,
                      "duration": 17400.0
                    }
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new OpenRouteServiceClientHttp(builder.build());
    }

    @Test
    void dosPuntosDevuelveDistanciaDuracionYPuntosDeLaRuta() {
        mockServer.expect(requestTo(startsWith("https://api.openrouteservice.org/v2/directions/driving-car/geojson")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.coordinates[0][0]").value(-64.1888))
                .andExpect(jsonPath("$.coordinates[0][1]").value(-31.4201))
                .andExpect(jsonPath("$.coordinates[1][0]").value(-60.6393))
                .andExpect(jsonPath("$.coordinates[1][1]").value(-32.9468))
                .andRespond(withSuccess(JSON_GEOJSON_VALIDO, MediaType.APPLICATION_JSON));

        List<Coordenada> puntos = List.of(
                new Coordenada(-31.4201, -64.1888),
                new Coordenada(-32.9468, -60.6393)
        );

        Ruta ruta = client.calcularRuta(puntos);

        assertNotNull(ruta);
        assertEquals(401.5, ruta.distanciaKm(), 0.001);
        assertEquals(290.0, ruta.duracionMin(), 0.001);
        assertEquals(3, ruta.puntos().size());
        assertEquals(-31.4201, ruta.puntos().get(0).lat(), 0.0001);
        assertEquals(-64.1888, ruta.puntos().get(0).lng(), 0.0001);
        assertEquals(-32.9468, ruta.puntos().get(2).lat(), 0.0001);
        assertEquals(-60.6393, ruta.puntos().get(2).lng(), 0.0001);

        mockServer.verify();
    }

    @Test
    void respuestaSinRutaFeaturesVacioLanzaServicioExternoException() {
        String jsonSinRuta = """
                {
                  "type": "FeatureCollection",
                  "features": []
                }
                """;

        mockServer.expect(requestTo(startsWith("https://api.openrouteservice.org/v2/directions/driving-car/geojson")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonSinRuta, MediaType.APPLICATION_JSON));

        List<Coordenada> puntos = List.of(
                new Coordenada(-31.4201, -64.1888),
                new Coordenada(-32.9468, -60.6393)
        );

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> client.calcularRuta(puntos)
        );

        assertTrue(ex.getMessage().contains("OpenRouteService no encontró una ruta válida"));
        mockServer.verify();
    }

    @Test
    void respuestaIncompletaSinSummaryLanzaServicioExternoException() {
        String jsonIncompleto = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {}
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(startsWith("https://api.openrouteservice.org/v2/directions/driving-car/geojson")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonIncompleto, MediaType.APPLICATION_JSON));

        List<Coordenada> puntos = List.of(
                new Coordenada(-31.4201, -64.1888),
                new Coordenada(-32.9468, -60.6393)
        );

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> client.calcularRuta(puntos)
        );

        assertTrue(ex.getMessage().contains("datos de resumen"));
        mockServer.verify();
    }

    @Test
    void openRouteServiceCaidoResponde500LanzaServicioExternoException() {
        mockServer.expect(requestTo(startsWith("https://api.openrouteservice.org/v2/directions/driving-car/geojson")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<Coordenada> puntos = List.of(
                new Coordenada(-31.4201, -64.1888),
                new Coordenada(-32.9468, -60.6393)
        );

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> client.calcularRuta(puntos)
        );

        assertTrue(ex.getMessage().contains("OpenRouteService no responde o devolvió un error"));
        mockServer.verify();
    }

    @Test
    void openRouteServiceConErrorDeRedLanzaServicioExternoException() {
        mockServer.expect(requestTo(startsWith("https://api.openrouteservice.org/v2/directions/driving-car/geojson")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Connection reset by peer")));

        List<Coordenada> puntos = List.of(
                new Coordenada(-31.4201, -64.1888),
                new Coordenada(-32.9468, -60.6393)
        );

        ServicioExternoException ex = assertThrows(
                ServicioExternoException.class,
                () -> client.calcularRuta(puntos)
        );

        assertTrue(ex.getMessage().contains("OpenRouteService no responde"));
        mockServer.verify();
    }

    @Test
    void parametrosInvalidosLanzaIllegalArgumentExceptionSinLlamarAlServicio() {
        assertThrows(IllegalArgumentException.class, () -> client.calcularRuta(null));
        assertThrows(IllegalArgumentException.class, () -> client.calcularRuta(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.calcularRuta(List.of(new Coordenada(-31.0, -64.0))));
        assertThrows(IllegalArgumentException.class, () -> client.calcularRuta(Arrays.asList(new Coordenada(-31.0, -64.0), null)));
    }

    @Test
    void resolucionDeClaveDeApiExplicitayEnv() {
        assertEquals("mi-clave-exp", OpenRouteServiceClientHttp.resolverApiKey("mi-clave-exp"));
        assertEquals("mi-clave-trim", OpenRouteServiceClientHttp.resolverApiKey("  mi-clave-trim  "));
    }

    @Test
    void clienteRealEnviaClaveEnEncabezadoAuthorizationAlServidor() throws IOException {
        AtomicReference<String> authCapturado = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/directions/driving-car/geojson", exchange -> {
            authCapturado.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] response = JSON_GEOJSON_VALIDO.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            OpenRouteServiceClientHttp clienteConClave = new OpenRouteServiceClientHttp(baseUrl, API_KEY);

            List<Coordenada> puntos = List.of(
                    new Coordenada(-31.4201, -64.1888),
                    new Coordenada(-32.9468, -60.6393)
            );

            Ruta ruta = clienteConClave.calcularRuta(puntos);

            assertNotNull(ruta);
            assertEquals(API_KEY, authCapturado.get(), "Debe enviar la API key en el encabezado Authorization");
            assertEquals(API_KEY, clienteConClave.getApiKey());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void noReenviaTokenJwtDeUsuarioAutenticadoEnSecurityContext() throws IOException {
        AtomicReference<String> authCapturado = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/directions/driving-car/geojson", exchange -> {
            authCapturado.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] response = JSON_GEOJSON_VALIDO.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        Jwt jwt = Jwt.withTokenValue("token-secreto-usuario-jwt")
                .header("alg", "none")
                .claim("sub", "usuario-123")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            OpenRouteServiceClientHttp clienteConClave = new OpenRouteServiceClientHttp(baseUrl, API_KEY);

            List<Coordenada> puntos = List.of(
                    new Coordenada(-31.4201, -64.1888),
                    new Coordenada(-32.9468, -60.6393)
            );

            Ruta ruta = clienteConClave.calcularRuta(puntos);

            assertNotNull(ruta);
            // El encabezado Authorization debe ser la API key de OpenRouteService, nunca el Bearer JWT del usuario
            assertEquals(API_KEY, authCapturado.get());
            assertFalse(authCapturado.get().contains("token-secreto-usuario-jwt"));
            assertFalse(authCapturado.get().startsWith("Bearer "));
        } finally {
            SecurityContextHolder.clearContext();
            server.stop(0);
        }
    }

    @Test
    void servidorLentoSuperaTimeoutLecturaYLanzaServicioExternoExceptionEnCincoSegundos() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/directions/driving-car/geojson", exchange -> {
            try {
                // Simula un servidor que tarda 8 segundos en responder
                Thread.sleep(8000);
                byte[] response = JSON_GEOJSON_VALIDO.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Conexión cortada por timeout del cliente
            }
        });
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            OpenRouteServiceClientHttp clienteConTimeouts = new OpenRouteServiceClientHttp(baseUrl, API_KEY);

            List<Coordenada> puntos = List.of(
                    new Coordenada(-31.4201, -64.1888),
                    new Coordenada(-32.9468, -60.6393)
            );

            long inicio = System.currentTimeMillis();
            ServicioExternoException ex = assertThrows(
                    ServicioExternoException.class,
                    () -> clienteConTimeouts.calcularRuta(puntos)
            );
            long duracionMs = System.currentTimeMillis() - inicio;

            assertTrue(ex.getMessage().contains("OpenRouteService no responde"));
            // El servidor tardaba 8s, pero el timeout de lectura de 5s debe cortar antes (entre 4.5s y 7.5s)
            assertTrue(duracionMs >= 4500, "Debería esperar al menos el timeout de lectura (~5s), tardó: " + duracionMs + " ms");
            assertTrue(duracionMs < 7500, "Debería fallar antes de los 8s del servidor por el timeout de lectura (~5s), tardó: " + duracionMs + " ms");
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}

