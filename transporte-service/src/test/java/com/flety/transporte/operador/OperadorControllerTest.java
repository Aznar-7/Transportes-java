package com.flety.transporte.operador;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.transporte.TokensDePrueba;
import com.flety.transporte.documento.Documento;
import com.flety.transporte.documento.DocumentoRepository;
import com.flety.transporte.documento.EstadoDocumento;
import com.flety.transporte.documento.TipoDocumentoRepository;
import com.flety.transporte.transportista.EstadoTransportista;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class OperadorControllerTest {

    private static final String BASE = "/api/transporte/operador";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    @Autowired
    TipoDocumentoRepository tipos;

    @Autowired
    DocumentoRepository documentos;

    Transportista perez;
    Documento documento;

    @BeforeEach
    void cargarDatos() {
        perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        Unidad unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
        documento = documentos.save(new Documento(tipos.findAll().getFirst(), unidad, "A-123", Instant.now().plus(30, ChronoUnit.DAYS), null));
    }

    @Test
    void verificaUnTransportistaPendiente() throws Exception {
        mockMvc.perform(patch(BASE + "/transportistas/{id}/verificacion", perez.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VERIFICADO"));

        assertThat(perez.getEstado()).isEqualTo(EstadoTransportista.VERIFICADO);
    }

    @Test
    void verificarDosVecesDevuelve409() throws Exception {
        perez.verificar();

        mockMvc.perform(patch(BASE + "/transportistas/{id}/verificacion", perez.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isConflict());
    }

    @Test
    void verificarUnTransportistaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch(BASE + "/transportistas/{id}/verificacion", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaTransportistasFiltrandoPorEstado() throws Exception {
        mockMvc.perform(get(BASE + "/transportistas").param("estado", "VERIFICADO")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(BASE + "/transportistas").param("estado", "PENDIENTE_VERIFICACION")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unEstadoQueNoExisteDevuelve400() throws Exception {
        mockMvc.perform(get(BASE + "/documentos").param("estado", "CUALQUIERA")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apruebaUnDocumentoEnRevision() throws Exception {
        revisar("{\"aprobado\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        assertThat(documento.getEstado()).isEqualTo(EstadoDocumento.APROBADO);
    }

    @Test
    void rechazaUnDocumentoConMotivo() throws Exception {
        revisar("{\"aprobado\": false, \"motivo\": \"Foto ilegible\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("Foto ilegible"));
    }

    @Test
    void unRechazoSinMotivoDevuelve400() throws Exception {
        revisar("{\"aprobado\": false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivoPresente").value("un rechazo necesita motivo"));

        assertThat(documento.getEstado()).isEqualTo(EstadoDocumento.EN_REVISION);
    }

    @Test
    void revisarDosVecesDevuelve409() throws Exception {
        documento.aprobar();

        revisar("{\"aprobado\": false, \"motivo\": \"Me arrepentí\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void listaDocumentosFiltrandoPorEstado() throws Exception {
        mockMvc.perform(get(BASE + "/documentos").param("estado", "EN_REVISION")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("A-123"));
    }

    @Test
    void unTransportistaNoPuedeUsarLasRutasDelOperadorDevuelve403() throws Exception {
        mockMvc.perform(patch(BASE + "/transportistas/{id}/verificacion", perez.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isForbidden());

        assertThat(perez.getEstado()).isEqualTo(EstadoTransportista.PENDIENTE_VERIFICACION);
    }

    private ResultActions revisar(String cuerpo) throws Exception {
        return mockMvc.perform(patch(BASE + "/documentos/{id}/revision", documento.getId())
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
