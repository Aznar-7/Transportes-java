package com.flety.commons.http;

import com.flety.commons.excepciones.ServicioNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FabricaRestClientTest {

    private static final String URL = "http://transporte-service:8091/internal/retornos/1";

    private RestClient restClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // La fábrica ya entrega el builder configurado con timeouts e interceptores
        RestClient.Builder builder = FabricaRestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @Test
    @DisplayName("Cuando el servicio remoto agota el timeout en GET tras reintento, lanza ServicioNoDisponibleException")
    void timeoutEnGetLanzaServicioNoDisponibleException() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> restClient.get().uri(URL).retrieve().toBodilessEntity())
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("tras reintento");

        mockServer.verify();
    }

    @Test
    @DisplayName("Petición GET que falla la primera vez (503) tiene éxito en el reintento")
    void getFallaPrimeroYReintentoTieneExito() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\": 1}", MediaType.APPLICATION_JSON));

        var respuesta = restClient.get().uri(URL).retrieve().toBodilessEntity();
        assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();

        mockServer.verify();
    }

    @Test
    @DisplayName("Petición GET que falla en ambos intentos lanza ServicioNoDisponibleException (503)")
    void getFallaAmbosIntentosLanza503() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> restClient.get().uri(URL).retrieve().toBodilessEntity())
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("503");

        mockServer.verify();
    }

    @Test
    @DisplayName("Petición GET que responde 500 y en el reintento sufre timeout hace exactamente 2 llamadas en total")
    void getFalla500YReintentoTimeoutHaceMaximoDosLlamadas() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> restClient.get().uri(URL).retrieve().toBodilessEntity())
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("tras reintento");

        mockServer.verify();
    }

    @Test
    @DisplayName("Petición POST no idempotente no realiza reintento ante falla")
    void postNoIdempotenteNoReintenta() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> restClient.post().uri(URL).retrieve().toBodilessEntity())
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("503");

        mockServer.verify();
    }
}
