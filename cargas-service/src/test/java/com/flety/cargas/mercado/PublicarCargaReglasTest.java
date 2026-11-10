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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RN-07 y RN-08: el pedido está bien formado (por eso no es 400), pero el negocio no permite publicar: 422.
 * dador1 está registrado como empresa; dador2 cambia según el caso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class PublicarCargaReglasTest {

    private static final Instant CARGA_DESDE = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final String CORDOBA = "Córdoba";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DadorRepository dadores;

    @Autowired
    CargaRepository cargas;

    private long cargasAntes;

    @BeforeEach
    void preparar() {
        dadores.save(new Dador("sub-dador1", "30546671166", "Acme SA", "+5493514445566", true));
        cargasAntes = cargas.count();
    }

    @Test
    void rn07SinOrganizacionRegistradaDevuelve422() throws Exception {
        publicar(TokensDePrueba.OTRO_DADOR, CORDOBA, "\"declaraNoPeligrosa\": true, \"declaraNoSobredimensionada\": true")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Para publicar tenés que registrar tu organización (RN-07)"));

        assertThat(cargas.count()).isEqualTo(cargasAntes);
    }

    @Test
    void rn07SinDeclararQueContrataComoEmpresaDevuelve422() throws Exception {
        dadores.save(new Dador("sub-dador2", "33693450239", "Particular SA", "+5493514440000", false));

        publicar(TokensDePrueba.OTRO_DADOR, CORDOBA, "\"declaraNoPeligrosa\": true, \"declaraNoSobredimensionada\": true")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Para publicar tenés que declarar que contratás como empresa (RN-07)"));

        assertThat(cargas.count()).isEqualTo(cargasAntes);
    }

    @Test
    void rn08UnaCargaPeligrosaDevuelve422() throws Exception {
        publicar(TokensDePrueba.DADOR, CORDOBA, "\"declaraNoPeligrosa\": false, \"declaraNoSobredimensionada\": true")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Para publicar tenés que declarar que la carga no es peligrosa (RN-08)"));

        assertThat(cargas.count()).isEqualTo(cargasAntes);
    }

    @Test
    void rn08UnaCargaSobredimensionadaDevuelve422() throws Exception {
        publicar(TokensDePrueba.DADOR, CORDOBA, "\"declaraNoPeligrosa\": true, \"declaraNoSobredimensionada\": false")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Para publicar tenés que declarar que la carga no está sobredimensionada (RN-08)"));

        assertThat(cargas.count()).isEqualTo(cargasAntes);
    }

    @Test
    void rn08NoMandarLasDeclaracionesEsNoDeclarar() throws Exception {
        publicar(TokensDePrueba.DADOR, CORDOBA, "\"rubroDeRelleno\": null")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Para publicar tenés que declarar que la carga no es peligrosa (RN-08)"));
    }

    /** Una localidad que Georef no conoce daría 503: si responde 422 es que las reglas corren antes de llamarlo. */
    @Test
    void lasReglasSeControlanAntesDeLlamarAGeoref() throws Exception {
        publicar(TokensDePrueba.DADOR, "Pueblo que Georef no conoce",
                "\"declaraNoPeligrosa\": false, \"declaraNoSobredimensionada\": true")
                .andExpect(status().isUnprocessableContent());
    }

    private ResultActions publicar(String token, String localidadOrigen, String declaraciones) throws Exception {
        String cuerpo = """
                {"origen": {"provincia": "Córdoba", "localidad": "%s", "direccion": "Av. Colón 1000"},
                 "destino": {"provincia": "Santa Fe", "localidad": "Rosario", "direccion": "Bv. Oroño 500"},
                 "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(localidadOrigen, CARGA_DESDE, CARGA_DESDE.plus(8, ChronoUnit.HOURS),
                CARGA_DESDE.plus(1, ChronoUnit.DAYS), CARGA_DESDE.plus(2, ChronoUnit.DAYS), declaraciones);
        return mockMvc.perform(post("/api/cargas").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
