package com.flety.transporte.documento;

import com.flety.commons.contrato.TipoCarroceria;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class DocumentoControllerTest {

    private static final String VIGENTE = Instant.now().plus(365, ChronoUnit.DAYS).toString();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    @Autowired
    TipoDocumentoRepository tipos;

    Unidad unidad;

    @BeforeEach
    void darDeAltaLaUnidad() {
        Transportista perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
    }

    @Test
    void losTiposDeDocumentoVienenDeLosDatosSemilla() {
        assertThat(tipos.findAll()).extracting(TipoDocumento::getNombre).contains(
                "Póliza de responsabilidad civil", "Póliza de carga", "Revisión técnica", "Licencia profesional");
    }

    @Test
    void unDocumentoVigenteQuedaEnRevision() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Revisión técnica"), "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.tipo").value("Revisión técnica"))
                .andExpect(jsonPath("$.unidadId").value(unidad.getId()));
    }

    @Test
    void unDocumentoVencidoDevuelve400() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Revisión técnica"), "\"2020-01-01T00:00:00Z\"", "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.vigencia").value("el documento ya está vencido"));
    }

    @Test
    void enUnaUnidadAjenaDevuelve403() throws Exception {
        cargar(TokensDePrueba.OTRO_TRANSPORTISTA, unidad.getId(), pedido(tipo("Revisión técnica"), "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enUnaUnidadInexistenteDevuelve404() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, 999999L, pedido(tipo("Revisión técnica"), "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conUnTipoInexistenteDevuelve404() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(999999L, "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unTipoDelTransportistaNoSeCargaEnLaUnidad() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Licencia profesional"), "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unTipoQueVenceNecesitaVigencia() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Revisión técnica"), "null", "null"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void laPolizaDeCargaNecesitaSumaAsegurada() throws Exception {
        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Póliza de carga"), "\"" + VIGENTE + "\"", "null"))
                .andExpect(status().isUnprocessableEntity());

        cargar(TokensDePrueba.TRANSPORTISTA, unidad.getId(), pedido(tipo("Póliza de carga"), "\"" + VIGENTE + "\"", "50000000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sumaAsegurada").value(50000000.00));
    }

    private ResultActions cargar(String token, Long unidadId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/transporte/unidades/{id}/documentos", unidadId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Long tipo(String nombre) {
        return tipos.findAll().stream().filter(t -> t.getNombre().equals(nombre)).findFirst().orElseThrow().getId();
    }

    private static String pedido(Long tipoId, String vigencia, String suma) {
        return """
                {"tipoDocumentoId": %d, "numero": "A-123", "vigencia": %s, "sumaAsegurada": %s}
                """.formatted(tipoId, vigencia, suma);
    }
}
