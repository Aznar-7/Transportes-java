package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class CargaControllerTest {

    private static final String URL = "/api/cargas";
    private static final Instant CARGA_DESDE = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private static final String ORIGEN_CORDOBA =
            "{\"provincia\": \"Córdoba\", \"localidad\": \"Córdoba\", \"direccion\": \"Av. Colón 1000\"}";
    private static final String DESTINO_ROSARIO =
            "{\"provincia\": \"Santa Fe\", \"localidad\": \"Rosario\", \"direccion\": \"Bv. Oroño 500\"}";
    private static final String DECLARA_TODO = "\"declaraNoPeligrosa\": true, \"declaraNoSobredimensionada\": true";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DadorRepository dadores;

    @Autowired
    CargaRepository cargas;

    @BeforeEach
    void dadorRegistrado() {
        dadores.save(new Dador("sub-dador1", "30546671166", "Acme SA", "+5493514445566", true));
    }

    @Test
    void conTodoBienDevuelve201YQuedaPublicadaDelDadorDelToken() throws Exception {
        String respuesta = publicar(TokensDePrueba.DADOR, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.estado").value("PUBLICADA"))
                .andExpect(jsonPath("$.origen.direccion").value("Av. Colón 1000"))
                .andExpect(jsonPath("$.creadaEn").exists())
                .andExpect(jsonPath("$.dadorSub").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(respuesta.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        assertThat(cargas.findById(id).orElseThrow().getDadorSub()).isEqualTo("sub-dador1");
    }

    @Test
    void sinCoordenadasLasPideAGeoref() throws Exception {
        publicar(TokensDePrueba.DADOR, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen.lat").value(-31.4201))
                .andExpect(jsonPath("$.destino.lng").value(-60.6393));
    }

    @Test
    void conCoordenadasLasUsaSinPedirleNadaAGeoref() throws Exception {
        String origen = """
                {"provincia": "Córdoba", "localidad": "Pueblo que Georef no conoce", "direccion": "Ruta 9 km 500",
                 "lat": -31.5, "lng": -64.5}
                """;

        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen.lat").value(-31.5))
                .andExpect(jsonPath("$.origen.lng").value(-64.5));
    }

    @Test
    void siGeorefNoUbicaLaLocalidadDevuelve503ConMensajeClaro() throws Exception {
        String origen = "{\"provincia\": \"Córdoba\", \"localidad\": \"Pueblo que Georef no conoce\", \"direccion\": \"Ruta 9\"}";

        long antes = cargas.count();
        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("No pudimos ubicar la localidad Pueblo que Georef no conoce, Córdoba; enviá las coordenadas"));
        assertThat(cargas.count()).isEqualTo(antes);
    }

    @Test
    void conUnaSolaCoordenadaDevuelve400() throws Exception {
        String origen = "{\"provincia\": \"Córdoba\", \"localidad\": \"Córdoba\", \"direccion\": \"Colón 1\", \"lat\": -31.5}";

        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['origen.coordenadasCompletas']").exists());
    }

    @Test
    void conDatosFaltantesDevuelve400ConLosCampos() throws Exception {
        publicar(TokensDePrueba.DADOR, "{" + DECLARA_TODO + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.origen").exists())
                .andExpect(jsonPath("$.errores.cargaDesde").exists())
                .andExpect(jsonPath("$.errores.rubro").exists())
                .andExpect(jsonPath("$.errores.carroceriasAceptadas").exists());
    }

    @Test
    void conVentanasIncoherentesDevuelve400() throws Exception {
        String cuerpo = """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(ORIGEN_CORDOBA, DESTINO_ROSARIO, CARGA_DESDE, CARGA_DESDE.plus(8, ChronoUnit.HOURS),
                CARGA_DESDE.minus(1, ChronoUnit.DAYS), CARGA_DESDE.plus(1, ChronoUnit.DAYS), DECLARA_TODO);

        publicar(TokensDePrueba.DADOR, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ventanasCoherentes").exists());
    }

    @Test
    void conUnaVentanaDeCargaYaCerradaDevuelve400() throws Exception {
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String cuerpo = """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(ORIGEN_CORDOBA, DESTINO_ROSARIO, ayer.minus(1, ChronoUnit.HOURS), ayer,
                ayer, ayer.plus(5, ChronoUnit.DAYS), DECLARA_TODO);

        publicar(TokensDePrueba.DADOR, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cargaHasta").exists());
    }

    @Test
    void conUnaCarroceriaQueNoExisteDevuelve400() throws Exception {
        String cuerpo = cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO).replace("\"SIDER\"", "\"BICICLETA\"");

        publicar(TokensDePrueba.DADOR, cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTransportistaNoPuedePublicar() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isForbidden());
    }

    private ResultActions publicar(String token, String cuerpo) throws Exception {
        return mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Una carga válida; cada test cambia lo que quiere probar. */
    private static String cuerpo(String origen, String destino, String declaraciones) {
        return """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(origen, destino, CARGA_DESDE, CARGA_DESDE.plus(8, ChronoUnit.HOURS),
                CARGA_DESDE.plus(1, ChronoUnit.DAYS), CARGA_DESDE.plus(2, ChronoUnit.DAYS), declaraciones);
    }
}
