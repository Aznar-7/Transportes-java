package com.flety.commons.geo;

import com.flety.commons.excepciones.ServicioExternoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

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
}
