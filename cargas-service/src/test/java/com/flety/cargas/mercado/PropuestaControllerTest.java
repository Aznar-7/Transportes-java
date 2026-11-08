package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.transporte.TransporteClientFake;
import com.flety.cargas.viajes.ViajesDePrueba;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.contrato.UbicacionDTO;
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
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Propuestas con la seguridad real y el fake de TransporteClient. Cada test usa una unidad y un retorno propios porque
 * el fake vive mientras viva el contexto de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, TransporteDePrueba.class, ViajesDePrueba.class})
@Transactional
class PropuestaControllerTest {

    private static final Instant CARGA_DESDE = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final AtomicLong RETORNOS = new AtomicLong(2000);
    private static final String DUENO = "sub-transportista1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CargaRepository cargas;

    @Autowired
    PropuestaRepository propuestas;

    @Autowired
    TransporteClientFake transporte;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void aceptarElPrecioDevuelve201ConElPrecioDelDadorYLaCargaConPropuestas() throws Exception {
        Carga carga = cargas.save(cargaAbierta());
        Long unidad = unidadHabilitada();

        proponer(carga.getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tipo").value("ACEPTA_PRECIO"))
                .andExpect(jsonPath("$.precio").value(850000.00))
                .andExpect(jsonPath("$.transportistaId").value(3))
                .andExpect(jsonPath("$.transportistaSub").doesNotExist());

        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.CON_PROPUESTAS);
        assertThat(propuestas.existsByCargaIdAndTransportistaSub(carga.getId(), DUENO)).isTrue();
    }

    @Test
    void unaContraofertaGuardaSuPrecio() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(cargas.save(cargaAbierta()).getId(), contraoferta(unidad, retorno(unidad), "800000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONTRAOFERTA"))
                .andExpect(jsonPath("$.precio").value(800000));
    }

    @Test
    void unaContraofertaIgualAlPrecioDelDadorDevuelve422() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(cargas.save(cargaAbierta()).getId(), contraoferta(unidad, retorno(unidad), "850000"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void venceALas24HorasSiLaVentanaCierraDespues() throws Exception {
        Long unidad = unidadHabilitada();

        String respuesta = proponer(cargas.save(cargaAbierta()).getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Instant vencimiento = Instant.parse(respuesta.replaceAll(".*\"vencimiento\":\"([^\"]+)\".*", "$1"));
        assertThat(vencimiento).isBetween(Instant.now().plus(23, ChronoUnit.HOURS), Instant.now().plus(25, ChronoUnit.HOURS));
    }

    @Test
    void venceAlCerrarLaVentanaSiCierraAntesDe24Horas() throws Exception {
        Carga carga = cargaAbierta();
        Instant cierre = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        carga.setCargaDesde(cierre.minus(1, ChronoUnit.HOURS));
        carga.setCargaHasta(cierre);
        Long unidad = unidadHabilitada();

        proponer(cargas.save(carga).getId(), aceptaPrecio(unidad, retorno(unidad, DUENO, "PUBLICADO", unidad, cierre.minus(2, ChronoUnit.HOURS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vencimiento").value(cierre.toString()));
    }

    @Test
    void unaContraofertaSinPrecioDevuelve400() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(cargas.save(cargaAbierta()).getId(),
                "{\"unidadId\": %d, \"retornoId\": %d, \"tipo\": \"CONTRAOFERTA\"}".formatted(unidad, retorno(unidad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioCoherente").exists());
    }

    @Test
    void aceptarElPrecioMandandoOtroDevuelve400() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(cargas.save(cargaAbierta()).getId(),
                "{\"unidadId\": %d, \"retornoId\": %d, \"tipo\": \"ACEPTA_PRECIO\", \"precio\": 1}".formatted(unidad, retorno(unidad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conUnaUnidadDeOtroDevuelve403() throws Exception {
        Long unidad = unidadHabilitada();
        Long retorno = retorno(unidad);
        transporte.conHabilitacion(habilitacion(unidad, "sub-otro", true, TipoCarroceria.SIDER));

        proponer(cargas.save(cargaAbierta()).getId(), aceptaPrecio(unidad, retorno))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Esta unidad no es tuya"));
    }

    @Test
    void conUnRetornoDeOtroDevuelve403() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(cargas.save(cargaAbierta()).getId(), aceptaPrecio(unidad, retorno(unidad, "sub-otro", "PUBLICADO", unidad, CARGA_DESDE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Este retorno no es tuyo"));
    }

    @Test
    void rn06ConValorDeclaradoMayorALaSumaAseguradaPideAceptarElRiesgo() throws Exception {
        Carga carga = cargaAbierta();
        carga.setValorDeclarado(new BigDecimal("50000000.01"));
        Long unidad = unidadHabilitada();

        proponer(cargas.save(carga).getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereAceptacionRiesgo").value(true));
    }

    @Test
    void rn06ConValorDeclaradoIgualALaSumaAseguradaNoPideNada() throws Exception {
        Carga carga = cargaAbierta();
        carga.setValorDeclarado(new BigDecimal("50000000.00"));
        Long unidad = unidadHabilitada();

        proponer(cargas.save(carga).getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereAceptacionRiesgo").value(false));
    }

    @Test
    void rn15ConUnViajeDeLaUnidadQueSePisaDevuelve409() throws Exception {
        Long unidad = unidadHabilitada();
        fixture.viajeConfirmado(unidad, CARGA_DESDE.plus(1, ChronoUnit.HOURS));
        Carga carga = cargas.save(cargaAbierta());

        proponer(carga.getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La unidad ya tiene un viaje que se superpone con esta carga (RN-15)"));

        assertThat(propuestas.existsByCargaIdAndTransportistaSub(carga.getId(), DUENO)).isFalse();
    }

    @Test
    void rn15ConUnViajeDeLaUnidadEnOtrasFechasPasa() throws Exception {
        Long unidad = unidadHabilitada();
        fixture.viajeConfirmado(unidad, CARGA_DESDE.plus(30, ChronoUnit.DAYS));

        proponer(cargas.save(cargaAbierta()).getId(), aceptaPrecio(unidad, retorno(unidad)))
                .andExpect(status().isCreated());
    }

    @Test
    void unaCargaQueNoExisteDevuelve404() throws Exception {
        Long unidad = unidadHabilitada();

        proponer(999_999L, aceptaPrecio(unidad, retorno(unidad))).andExpect(status().isNotFound());
    }

    @Test
    void conTransporteCaidoDevuelve503YNoCreaNada() throws Exception {
        Carga carga = cargas.save(cargaAbierta());
        Long unidad = unidadHabilitada();
        Long retorno = retorno(unidad);
        transporte.simularCaida(true);
        try {
            proponer(carga.getId(), aceptaPrecio(unidad, retorno)).andExpect(status().isServiceUnavailable());
        } finally {
            transporte.simularCaida(false);
        }
        assertThat(propuestas.existsByCargaIdAndTransportistaSub(carga.getId(), DUENO)).isFalse();
        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.PUBLICADA);
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/cargas/1/propuestas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unDadorNoPuedeProponer() throws Exception {
        mockMvc.perform(post("/api/cargas/1/propuestas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(aceptaPrecio(1L, 1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elDadorVeLasPropuestasDeSuCarga() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Long primera = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva(), "sub-transportista1").getId();
        Long segunda = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva(), "sub-transportista2").getId();

        mockMvc.perform(get("/api/cargas/" + carga.getId() + "/propuestas")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(primera.intValue(), segunda.intValue())))
                .andExpect(jsonPath("$[0].transportistaSub").doesNotExist());
    }

    @Test
    void otroDadorNoVeLasPropuestas() throws Exception {
        mockMvc.perform(get("/api/cargas/" + fixture.cargaAbierta().getId() + "/propuestas")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_DADOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void unTransportistaNoVeLasPropuestasDeLaCarga() throws Exception {
        mockMvc.perform(get("/api/cargas/" + fixture.cargaAbierta().getId() + "/propuestas")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void elTransportistaRetiraSuPropuestaPendiente() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());

        mockMvc.perform(delete("/api/cargas/propuestas/" + propuesta.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNoContent());

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.RETIRADA);
    }

    @Test
    void otroTransportistaNoPuedeRetirarla() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());

        mockMvc.perform(delete("/api/cargas/propuestas/" + propuesta.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TERCERO))
                .andExpect(status().isForbidden());

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    void retirarUnaQueYaNoEstaPendienteDevuelve409() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());
        propuesta.setEstado(EstadoPropuesta.ACEPTADA);

        mockMvc.perform(delete("/api/cargas/propuestas/" + propuesta.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isConflict());
    }

    @Test
    void retirarUnaQueNoExisteDevuelve404() throws Exception {
        mockMvc.perform(delete("/api/cargas/propuestas/999999")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNotFound());
    }

    @Test
    void elDadorRechazaUnaYLaCargaSigueAbierta() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());

        mockMvc.perform(post("/api/cargas/propuestas/" + propuesta.getId() + "/rechazo")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    @Test
    void otroDadorNoPuedeRechazarla() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());

        mockMvc.perform(post("/api/cargas/propuestas/" + propuesta.getId() + "/rechazo")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_DADOR))
                .andExpect(status().isForbidden());

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    void rechazarUnaQueYaNoEstaPendienteDevuelve409() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());
        propuesta.setEstado(EstadoPropuesta.RETIRADA);

        mockMvc.perform(post("/api/cargas/propuestas/" + propuesta.getId() + "/rechazo")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isConflict());
    }

    ResultActions proponer(Long cargaId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/cargas/" + cargaId + "/propuestas")
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    static String aceptaPrecio(Long unidad, Long retorno) {
        return "{\"unidadId\": %d, \"retornoId\": %d, \"tipo\": \"ACEPTA_PRECIO\"}".formatted(unidad, retorno);
    }

    static String contraoferta(Long unidad, Long retorno, String precio) {
        return "{\"unidadId\": %d, \"retornoId\": %d, \"tipo\": \"CONTRAOFERTA\", \"precio\": %s}".formatted(unidad, retorno, precio);
    }

    private Long unidadHabilitada() {
        Long unidad = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(habilitacion(unidad, DUENO, true, TipoCarroceria.SIDER));
        return unidad;
    }

    private Long retorno(Long unidad) {
        return retorno(unidad, DUENO, "PUBLICADO", unidad, CARGA_DESDE);
    }

    private Long retorno(Long unidad, String sub, String estado, Long unidadDelRetorno, Instant salida) {
        Long id = RETORNOS.incrementAndGet();
        transporte.conRetorno(new RetornoInternoDTO(id, 3L, unidadDelRetorno, estado,
                new UbicacionDTO("Santa Fe", "Rosario", -32.9468, -60.6393),
                new UbicacionDTO("Córdoba", "Córdoba", -31.4201, -64.1888),
                salida, salida.plus(12, ChronoUnit.HOURS), 30.0, 401.5, 290.0, List.of(), false,
                TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00"), sub));
        return id;
    }

    private static HabilitacionUnidadDTO habilitacion(Long unidad, String sub, boolean habilitada, TipoCarroceria carroceria) {
        return new HabilitacionUnidadDTO(unidad, habilitada, habilitada ? List.of() : List.of("Falta la póliza de carga"),
                "AB123CD", carroceria, new BigDecimal("28.00"), new BigDecimal("90.00"), new BigDecimal("50000000.00"),
                3L, "Transportes Pérez SRL", "+54 351 555-1234", sub);
    }

    private static Carga cargaAbierta() {
        Carga carga = new Carga();
        carga.setDadorSub("sub-dador1");
        carga.setOrigen(new Ubicacion("Santa Fe", "Rosario", -32.9468, -60.6393, "Bv. Oroño 500"));
        carga.setDestino(new Ubicacion("Córdoba", "Villa María", -32.4075, -63.2402, "Sabattini 100"));
        carga.setCargaDesde(CARGA_DESDE);
        carga.setCargaHasta(CARGA_DESDE.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(CARGA_DESDE.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(CARGA_DESDE.plus(2, ChronoUnit.DAYS));
        carga.setRubro("Alimentos secos");
        carga.setPesoToneladas(new BigDecimal("12.50"));
        carga.setVolumenM3(new BigDecimal("40.00"));
        carga.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.SIDER));
        carga.setPrecioOfrecido(new BigDecimal("850000.00"));
        carga.setValorDeclarado(new BigDecimal("12000000.00"));
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        return carga;
    }
}
