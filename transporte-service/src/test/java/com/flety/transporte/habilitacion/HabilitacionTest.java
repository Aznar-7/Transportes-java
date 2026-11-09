package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.transporte.TokensDePrueba;
import com.flety.transporte.documento.Documento;
import com.flety.transporte.documento.DocumentoRepository;
import com.flety.transporte.documento.TipoDocumento;
import com.flety.transporte.documento.TipoDocumentoRepository;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RN-04 por los dos endpoints. Las fechas se arman relativas a "hasta" para que el test no dependa del día en que corre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class HabilitacionTest {

    private static final Instant HASTA = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant DESPUES = HASTA.plus(30, ChronoUnit.DAYS);
    private static final Instant ANTES = HASTA.minus(2, ChronoUnit.DAYS);

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
    Unidad unidad;

    @BeforeEach
    void cargarDatos() {
        perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
    }

    @Test
    void verificadoYConLosDocumentosAprobadosYVigentesEstaHabilitada() throws Exception {
        dejarEnRegla();

        mockMvc.perform(interna(unidad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true))
                .andExpect(jsonPath("$.motivos", empty()))
                .andExpect(jsonPath("$.sumaAseguradaCarga").value(50000000.00))
                .andExpect(jsonPath("$.transportistaSub").value("sub-transportista1"))
                .andExpect(jsonPath("$.dominio").value("AB123CD"));
    }

    @Test
    void sinVerificarYSinDocumentosDiceTodoLoQueFalta() throws Exception {
        mockMvc.perform(interna(unidad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(false))
                .andExpect(jsonPath("$.motivos", containsInAnyOrder(
                        "El transportista no está VERIFICADO",
                        "Falta el documento obligatorio Póliza de responsabilidad civil",
                        "Falta el documento obligatorio Póliza de carga",
                        "Falta el documento obligatorio Revisión técnica")))
                .andExpect(jsonPath("$.sumaAseguradaCarga").doesNotExist());
    }

    @Test
    void unDocumentoQueVenceAntesDeHastaLaDejaSinHabilitar() throws Exception {
        perez.verificar();
        aprobado("Póliza de responsabilidad civil", DESPUES, null);
        aprobado("Póliza de carga", DESPUES, new BigDecimal("50000000.00"));
        aprobado("Revisión técnica", ANTES, null);

        mockMvc.perform(interna(unidad.getId()))
                .andExpect(jsonPath("$.habilitada").value(false))
                .andExpect(jsonPath("$.motivos", contains(
                        "Revisión técnica vence el " + fecha(ANTES) + ", antes del " + fecha(HASTA))));
    }

    @Test
    void unDocumentoEnRevisionTodaviaNoCuenta() throws Exception {
        perez.verificar();
        aprobado("Póliza de responsabilidad civil", DESPUES, null);
        aprobado("Póliza de carga", DESPUES, new BigDecimal("50000000.00"));
        documentos.save(new Documento(tipo("Revisión técnica"), unidad, "RT-1", DESPUES, null));

        mockMvc.perform(interna(unidad.getId()))
                .andExpect(jsonPath("$.habilitada").value(false))
                .andExpect(jsonPath("$.motivos", contains("Falta el documento obligatorio Revisión técnica")));
    }

    @Test
    void laRespuestaInternaTieneLosMismosCamposQueElContrato() throws Exception {
        dejarEnRegla();
        JsonMapper json = JsonMapper.builder().build();
        JsonNode ejemplo = json.readTree(Files.readString(Path.of("../docs/contrato-interno/ejemplos/habilitacion-habilitada.json")));

        String cuerpo = mockMvc.perform(interna(unidad.getId())).andReturn().getResponse().getContentAsString();

        assertThat(campos(json.readTree(cuerpo))).isEqualTo(campos(ejemplo));
    }

    @Test
    void elInternoAceptaElTokenDeCualquierRol() throws Exception {
        mockMvc.perform(get("/internal/unidades/{id}/habilitacion", unidad.getId()).param("hasta", HASTA.toString())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk());
    }

    @Test
    void elInternoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/internal/unidades/{id}/habilitacion", unidad.getId()).param("hasta", HASTA.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elInternoSinHastaDevuelve400() throws Exception {
        mockMvc.perform(get("/internal/unidades/{id}/habilitacion", unidad.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elInternoConUnaUnidadInexistenteDevuelve404() throws Exception {
        mockMvc.perform(interna(999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void elDuenoVeQueLeFalta() throws Exception {
        mockMvc.perform(get("/api/transporte/unidades/{id}/habilitacion", unidad.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(false))
                .andExpect(jsonPath("$.motivos.length()").value(4));
    }

    @Test
    void otroTransportistaNoVeLaHabilitacionDeMiUnidad() throws Exception {
        mockMvc.perform(get("/api/transporte/unidades/{id}/habilitacion", unidad.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA))
                .andExpect(status().isForbidden());
    }

    private RequestBuilder interna(Long unidadId) {
        return get("/internal/unidades/{id}/habilitacion", unidadId).param("hasta", HASTA.toString())
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR_PARA_INTERNO);
    }

    private void dejarEnRegla() {
        perez.verificar();
        aprobado("Póliza de responsabilidad civil", DESPUES, null);
        aprobado("Póliza de carga", DESPUES, new BigDecimal("50000000.00"));
        aprobado("Revisión técnica", DESPUES, null);
    }

    private void aprobado(String tipo, Instant vigencia, BigDecimal suma) {
        Documento documento = new Documento(tipo(tipo), unidad, "N-" + tipo.length(), vigencia, suma);
        documento.aprobar();
        documentos.save(documento);
    }

    private TipoDocumento tipo(String nombre) {
        return tipos.findAll().stream().filter(t -> t.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private static String fecha(Instant instante) {
        return instante.toString().substring(0, 10);
    }

    private static Set<String> campos(JsonNode nodo) {
        return new HashSet<>(nodo.propertyNames());
    }
}
