package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.contrato.UbicacionDTO;
import com.flety.commons.geo.Coordenada;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los flujos A y B de punta a punta: todo cargas-service es real (controllers, seguridad, services, repositorios y
 * TransporteClientHttp) y solo transporte-service está simulado, con un servidor HTTP de verdad. Los datos se crean
 * por la API, como en la demo. Las ventanas están más lejos que en otros tests para no cruzarse con sus cargas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class FlujosDeMercadoTest {

    private static final TransporteSimulado TRANSPORTE = new TransporteSimulado();

    private static final Instant SALIDA = Instant.now().plus(800, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada VILLA_MARIA = new Coordenada(-32.4075, -63.2402);
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);
    private static final String DUENO = "sub-transportista1";
    private static final AtomicLong IDS = new AtomicLong(9000);

    @DynamicPropertySource
    static void apuntarATransporteSimulado(DynamicPropertyRegistry propiedades) {
        propiedades.add("flety.transporte.url", TRANSPORTE::url);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    private Long unidad;
    private Long retorno;

    @BeforeEach
    void transporteConUnaUnidadHabilitadaYSuRetorno() {
        TRANSPORTE.caer(false);
        unidad = IDS.incrementAndGet();
        retorno = IDS.incrementAndGet();
        cargarUnidad(true, new BigDecimal("50000000.00"), List.of());
        TRANSPORTE.responder("/internal/retornos/" + retorno, mapper.writeValueAsString(new RetornoInternoDTO(
                retorno, 3L, unidad, "PUBLICADO",
                new UbicacionDTO("Santa Fe", "Rosario", ROSARIO.lat(), ROSARIO.lng()),
                new UbicacionDTO("Córdoba", "Córdoba", CORDOBA.lat(), CORDOBA.lng()),
                SALIDA, SALIDA.plus(12, ChronoUnit.HOURS), 30.0, 401.5, 290.0,
                List.of(ROSARIO, VILLA_MARIA, CORDOBA), false, TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00"), DUENO)));
    }

    // Flujo A

    @Test
    void flujoA_elTransportistaEncuentraSoloLaCompatibleYSinDatosReservados() throws Exception {
        registrarDador();
        Long compatible = publicar("SIDER");
        publicar("TANQUE");

        buscar(TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].carga.id").value(compatible))
                .andExpect(jsonPath("$[0].desvioKm").isNumber())
                .andExpect(jsonPath("$[0].precioPorKm").isNumber())
                .andExpect(jsonPath("$[0].aproximado").value(false))
                .andExpect(jsonPath("$[0].carga.origen.localidad").value("Rosario"))
                .andExpect(jsonPath("$[0].carga.origen.direccion").doesNotExist());

        assertThat(TRANSPORTE.pedidos()).contains("/internal/retornos/" + retorno);
    }

    @Test
    void flujoA_elDetalleDeLaCargaTampocoMuestraLoReservado() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");

        String detalle = mockMvc.perform(get("/api/cargas/" + carga)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.localidad").value("Rosario"))
                .andReturn().getResponse().getContentAsString();

        assertThat(detalle).doesNotContain("Bv. Oroño", "+5493514445566", "Acme");
    }

    @Test
    void flujoA_otroTransportistaNoPuedeBuscarConEsteRetorno() throws Exception {
        buscar(TokensDePrueba.TERCERO).andExpect(status().isForbidden());
    }

    @Test
    void flujoA_unRetornoQueTransporteNoConoceDevuelve404() throws Exception {
        mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", "1")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNotFound());
    }

    @Test
    void flujoA_conTransporteCaidoDevuelve503() throws Exception {
        TRANSPORTE.caer(true);

        buscar(TokensDePrueba.TRANSPORTISTA).andExpect(status().isServiceUnavailable());
    }

    // Flujo B

    @Test
    void flujoB_laContraofertaQuedaPendienteYElDadorLaVe() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");

        contraofertar(carga, "800000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.precio").value(800000))
                .andExpect(jsonPath("$.requiereAceptacionRiesgo").value(false));

        estadoDeLaCarga(carga).andExpect(jsonPath("$.estado").value("CON_PROPUESTAS"));
        mockMvc.perform(get("/api/cargas/" + carga + "/propuestas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].unidadId").value(unidad));

        // RN-04: la habilitación se pide hasta el final de la ventana de descarga de la carga.
        assertThat(TRANSPORTE.pedidos()).contains("/internal/unidades/" + unidad + "/habilitacion?hasta="
                + SALIDA.plus(2, ChronoUnit.DAYS));
    }

    @Test
    void flujoB_unaSegundaPropuestaDelMismoTransportistaDevuelve409() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        contraofertar(carga, "800000").andExpect(status().isCreated());

        contraofertar(carga, "790000").andExpect(status().isConflict());
    }

    @Test
    void flujoB_conLaUnidadNoHabilitadaDevuelve422ConElMotivoDeTransporte() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        cargarUnidad(false, new BigDecimal("50000000.00"), List.of("La revisión técnica está vencida"));

        contraofertar(carga, "800000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La unidad no está habilitada: La revisión técnica está vencida"));

        estadoDeLaCarga(carga).andExpect(jsonPath("$.estado").value("PUBLICADA"));
    }

    @Test
    void flujoB_conValorDeclaradoMayorALaSumaAseguradaPideAceptarElRiesgo() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        cargarUnidad(true, new BigDecimal("1000000.00"), List.of());

        contraofertar(carga, "800000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereAceptacionRiesgo").value(true));
    }

    @Test
    void flujoB_conTransporteCaidoDevuelve503YNoCreaNada() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        TRANSPORTE.caer(true);

        contraofertar(carga, "800000").andExpect(status().isServiceUnavailable());

        estadoDeLaCarga(carga).andExpect(jsonPath("$.estado").value("PUBLICADA"));
        mockMvc.perform(get("/api/cargas/" + carga + "/propuestas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void flujoB_siElTransportistaSeRetiraElDadorYaNoPuedeRechazarla() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        Long propuesta = idDe(contraofertar(carga, "800000").andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/cargas/propuestas/" + propuesta)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNoContent());

        // Ya se retiró: el dador no la puede rechazar.
        mockMvc.perform(post("/api/cargas/propuestas/" + propuesta + "/rechazo")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isConflict());
    }

    /** De la búsqueda al viaje: lo que arma mercado es lo que usa la aceptación de viajes (flujo C). */
    @Test
    void deLaBusquedaAlViaje_elViajeNaceConLosContactosDeLasDosPartes() throws Exception {
        registrarDador();
        Long carga = publicar("SIDER");
        buscar(TokensDePrueba.TRANSPORTISTA).andExpect(jsonPath("$[0].carga.id").value(carga));
        Long propuesta = idDe(contraofertar(carga, "800000"));

        mockMvc.perform(post("/api/cargas/propuestas/" + propuesta + "/aceptacion")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.precioAcordado").value(800000))
                .andExpect(jsonPath("$.contactos.dador.nombre").value("Acme SA"))
                .andExpect(jsonPath("$.contactos.transportista.nombre").value("Transportes Pérez SRL"));

        estadoDeLaCarga(carga).andExpect(jsonPath("$.estado").value("ASIGNADA"));
    }

    private void registrarDador() throws Exception {
        mockMvc.perform(post("/api/dadores").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuit": "30546671166", "razonSocial": "Acme SA", "telefono": "+5493514445566", "declaraEmpresa": true}
                                """))
                .andExpect(status().isCreated());
    }

    private Long publicar(String carroceria) throws Exception {
        String cuerpo = """
                {"origen": {"provincia": "Santa Fe", "localidad": "Rosario", "direccion": "Bv. Oroño 500"},
                 "destino": {"provincia": "Córdoba", "localidad": "Villa María", "direccion": "Sabattini 100"},
                 "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["%s"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s",
                 "declaraNoPeligrosa": true, "declaraNoSobredimensionada": true}
                """.formatted(carroceria, SALIDA, SALIDA.plus(8, ChronoUnit.HOURS),
                SALIDA.plus(1, ChronoUnit.DAYS), SALIDA.plus(2, ChronoUnit.DAYS));
        return idDe(mockMvc.perform(post("/api/cargas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()));
    }

    private ResultActions buscar(String token) throws Exception {
        return mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", retorno.toString())
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions contraofertar(Long carga, String precio) throws Exception {
        return mockMvc.perform(post("/api/cargas/" + carga + "/propuestas")
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PropuestaControllerTest.contraoferta(unidad, retorno, precio)));
    }

    private ResultActions estadoDeLaCarga(Long carga) throws Exception {
        return mockMvc.perform(get("/api/cargas/" + carga).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR));
    }

    private void cargarUnidad(boolean habilitada, BigDecimal sumaAsegurada, List<String> motivos) {
        TRANSPORTE.responder("/internal/unidades/" + unidad + "/habilitacion", mapper.writeValueAsString(new HabilitacionUnidadDTO(
                unidad, habilitada, motivos, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"),
                new BigDecimal("90.00"), sumaAsegurada, 3L, "Transportes Pérez SRL", "+54 351 555-1234", DUENO)));
    }

    private static Long idDe(ResultActions resultado) throws Exception {
        Number id = JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
