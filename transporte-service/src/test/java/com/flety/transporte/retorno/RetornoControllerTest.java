package com.flety.transporte.retorno;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.fake.GeorefClientFake;
import com.flety.commons.fake.RoutingClientFake;
import com.flety.transporte.TokensDePrueba;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.transportista.TransportistaRepository;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los fakes de flety-commons hacen de Georef y de la API de rutas, así se prueban la caída y la localidad
 * desconocida sin Internet. Se registran solo en este test: el servicio real no tiene fakes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, RetornoControllerTest.ClientesFalsos.class})
@Transactional
class RetornoControllerTest {

    private static final String URL = "/api/transporte/retornos";
    private static final Instant DESDE = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant HASTA = DESDE.plus(12, ChronoUnit.HOURS);

    @TestConfiguration(proxyBeanMethods = false)
    static class ClientesFalsos {
        @Bean
        GeorefClientFake georef() {
            return new GeorefClientFake();
        }

        @Bean
        RoutingClientFake routing() {
            return new RoutingClientFake().conRuta(401.5, 290.0);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    @Autowired
    GeorefClientFake georef;

    @Autowired
    RoutingClientFake routing;

    Unidad unidad;

    @BeforeEach
    void darDeAltaLaUnidad() {
        Transportista perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
    }

    @AfterEach
    void levantarLasApis() {
        georef.simularCaida(false);
        routing.simularCaida(false);
    }

    @Test
    void conCoordenadasSeUsanSinLlamarAGeoref() throws Exception {
        georef.simularCaida(true);

        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Rosario", -32.9468, -60.6393),
                lugar("Córdoba", "Córdoba", -31.4201, -64.1888), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.origen.lat").value(-32.9468))
                .andExpect(jsonPath("$.rutaAproximada").value(false))
                .andExpect(jsonPath("$.rutaDistanciaKm").value(401.5))
                .andExpect(jsonPath("$.desvioMaximoKm").value(30.0));
    }

    @Test
    void sinCoordenadasLasPideAGeoref() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Villa María"), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destino.lat").value(-32.4075))
                .andExpect(jsonPath("$.destino.lng").value(-63.2402));
    }

    @Test
    void unaLocalidadQueGeorefNoUbicaDevuelve503() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Pueblo Inventado"), lugar("Córdoba", "Córdoba"), "")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("No pudimos ubicar la localidad Pueblo Inventado, Santa Fe: enviá las coordenadas"));
    }

    @Test
    void siElRuteoFallaQuedaConRutaAproximada() throws Exception {
        routing.simularCaida(true);

        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Córdoba"), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rutaAproximada").value(true))
                .andExpect(jsonPath("$.rutaDuracionMin").value(0.0))
                .andExpect(jsonPath("$.rutaPuntos.length()").value(2))
                // Rosario–Córdoba en línea recta (haversine) son unos 374,7 km.
                .andExpect(jsonPath("$.rutaDistanciaKm", closeTo(374.7, 0.5)));
    }

    @Test
    void respetaElDesvioMaximoPedido() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Córdoba"),
                ", \"desvioMaximoKm\": 50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desvioMaximoKm").value(50.0));
    }

    @Test
    void conUnaUnidadAjenaDevuelve403() throws Exception {
        publicar(TokensDePrueba.OTRO_TRANSPORTISTA, unidad.getId(), lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Córdoba"), "")
                .andExpect(status().isForbidden());
    }

    @Test
    void conUnaUnidadInexistenteDevuelve404() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, 999999L, lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Córdoba"), "")
                .andExpect(status().isNotFound());
    }

    @Test
    void conLaVentanaAlRevesDevuelve400() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unidadId": %d, "origen": %s, "destino": %s, "salidaDesde": "%s", "salidaHasta": "%s"}
                                """.formatted(unidad.getId(), lugar("Santa Fe", "Rosario"), lugar("Córdoba", "Córdoba"), HASTA, DESDE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ventanaValida").exists());
    }

    @Test
    void conUnaSolaCoordenadaDevuelve400() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, unidad.getId(),
                "{\"provincia\": \"Santa Fe\", \"localidad\": \"Rosario\", \"lat\": -32.9}", lugar("Córdoba", "Córdoba"), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['origen.coordenadasCompletas']").exists());
    }

    private ResultActions publicar(String token, Long unidadId, String origen, String destino, String extra) throws Exception {
        return mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"unidadId": %d, "origen": %s, "destino": %s, "salidaDesde": "%s", "salidaHasta": "%s"%s}
                        """.formatted(unidadId, origen, destino, DESDE, HASTA, extra)));
    }

    private static String lugar(String provincia, String localidad) {
        return "{\"provincia\": \"%s\", \"localidad\": \"%s\"}".formatted(provincia, localidad);
    }

    private static String lugar(String provincia, String localidad, double lat, double lng) {
        return "{\"provincia\": \"%s\", \"localidad\": \"%s\", \"lat\": %s, \"lng\": %s}".formatted(provincia, localidad, lat, lng);
    }
}
