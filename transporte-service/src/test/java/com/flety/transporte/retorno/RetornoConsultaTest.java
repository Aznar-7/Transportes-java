package com.flety.transporte.retorno;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.Ruta;
import com.flety.transporte.TokensDePrueba;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.transportista.TransportistaRepository;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class RetornoConsultaTest {

    private static final Instant DESDE = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    @Autowired
    RetornoRepository retornos;

    Unidad unidad;
    Retorno retorno;

    @BeforeEach
    void cargarDatos() {
        Transportista perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
        retorno = retornos.save(retorno(unidad, DESDE));
    }

    @Test
    void listaMisRetornosYFiltraPorEstado() throws Exception {
        Retorno cancelado = retornos.save(retorno(unidad, DESDE.plus(2, ChronoUnit.DAYS)));
        cancelado.cancelar();

        mockMvc.perform(get("/api/transporte/retornos").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/transporte/retornos").param("estado", "PUBLICADO")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(retorno.getId()));
    }

    @Test
    void otroTransportistaNoVeMisRetornos() throws Exception {
        mockMvc.perform(get("/api/transporte/retornos").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/transporte/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void veUnRetornoPropio() throws Exception {
        mockMvc.perform(get("/api/transporte/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.localidad").value("Rosario"))
                .andExpect(jsonPath("$.rutaPuntos.length()").value(2));
    }

    @Test
    void verUnRetornoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/transporte/retornos/{id}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelarUnRetornoPublicadoDevuelve204YQuedaCancelado() throws Exception {
        mockMvc.perform(delete("/api/transporte/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNoContent());

        assertThat(retorno.getEstado()).isEqualTo(EstadoRetorno.CANCELADO);
    }

    @Test
    void cancelarUnRetornoVencidoDevuelve409() throws Exception {
        retorno.vencer();

        mockMvc.perform(delete("/api/transporte/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El retorno ya está VENCIDO"));
    }

    @Test
    void cancelarElRetornoDeOtroDevuelve403() throws Exception {
        mockMvc.perform(delete("/api/transporte/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA))
                .andExpect(status().isForbidden());

        assertThat(retorno.getEstado()).isEqualTo(EstadoRetorno.PUBLICADO);
    }

    @Test
    void elInternoDevuelveElRetornoConLosDatosDeLaUnidad() throws Exception {
        mockMvc.perform(get("/internal/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.transportistaSub").value("sub-transportista1"))
                .andExpect(jsonPath("$.tipoCarroceria").value("SIDER"))
                .andExpect(jsonPath("$.unidadId").value(unidad.getId()))
                .andExpect(jsonPath("$.salidaDesde").value(DESDE.toString()));
    }

    @Test
    void laRespuestaInternaTieneLosMismosCamposQueElContrato() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode ejemplo = json.readTree(Files.readString(Path.of("../docs/contrato-interno/ejemplos/retorno.json")));

        String cuerpo = mockMvc.perform(get("/internal/retornos/{id}", retorno.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andReturn().getResponse().getContentAsString();

        assertThat(campos(json.readTree(cuerpo))).isEqualTo(campos(ejemplo));
    }

    @Test
    void elInternoConUnRetornoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/internal/retornos/{id}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void elInternoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/retornos/{id}", retorno.getId()))
                .andExpect(status().isUnauthorized());
    }

    private static Retorno retorno(Unidad unidad, Instant desde) {
        List<Coordenada> extremos = List.of(ROSARIO, CORDOBA);
        return new Retorno(unidad, new Ubicacion("Santa Fe", "Rosario", ROSARIO), new Ubicacion("Córdoba", "Córdoba", CORDOBA),
                desde, desde.plus(12, ChronoUnit.HOURS), 30.0, new Ruta(401.5, 290.0, extremos), false);
    }

    private static Set<String> campos(JsonNode nodo) {
        return new HashSet<>(nodo.propertyNames());
    }
}
