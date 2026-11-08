package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.transporte.TransporteClientFake;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.contrato.UbicacionDTO;
import com.flety.commons.geo.Coordenada;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/cargas/de-paso con el retorno del fake de TransporteClient. La base H2 se comparte con tests que no hacen
 * rollback: por eso las ventanas están casi un año adelante, donde no hay cargas de otros tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, TransporteDePrueba.class})
@Transactional
class CargasDePasoTest {

    private static final Instant SALIDA = Instant.now().plus(330, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada VILLA_MARIA = new Coordenada(-32.4075, -63.2402);
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);

    /** Los ids de retorno no se repiten porque el fake vive mientras viva el contexto de Spring. */
    private static final AtomicLong RETORNOS = new AtomicLong(1000);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CargaRepository cargas;

    @Autowired
    TransporteClientFake transporte;

    @Test
    void apareceUnaCargaCompatibleSinDatosReservados() throws Exception {
        Long id = cargas.save(cargaCompatible()).getId();

        buscar(retorno("PUBLICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id.intValue())))
                .andExpect(jsonPath("$[0].origen.localidad").value("Rosario"))
                .andExpect(jsonPath("$[0].origen.direccion").doesNotExist())
                .andExpect(jsonPath("$[0].origen.lat").doesNotExist());
    }

    @Test
    void soloAparecenLasAbiertas() throws Exception {
        Long publicada = cargas.save(cargaCompatible()).getId();
        Carga conPropuestas = cargaCompatible();
        conPropuestas.setEstado(EstadoCarga.CON_PROPUESTAS);
        Long conPropuestasId = cargas.save(conPropuestas).getId();
        Carga asignada = cargaCompatible();
        asignada.setEstado(EstadoCarga.ASIGNADA);
        Long asignadaId = cargas.save(asignada).getId();

        buscar(retorno("PUBLICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(publicada.intValue())))
                .andExpect(jsonPath("$[*].id", hasItem(conPropuestasId.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(asignadaId.intValue()))));
    }

    @Test
    void laVentanaDeCargaTieneQuePisarseConLaSalida() throws Exception {
        Carga antes = cargaCompatible();
        antes.setCargaDesde(SALIDA.minus(20, ChronoUnit.HOURS));
        antes.setCargaHasta(SALIDA.minus(1, ChronoUnit.HOURS));
        Long antesId = cargas.save(antes).getId();
        Carga despues = cargaCompatible();
        despues.setCargaDesde(SALIDA.plus(13, ChronoUnit.HOURS));
        despues.setCargaHasta(SALIDA.plus(20, ChronoUnit.HOURS));
        Long despuesId = cargas.save(despues).getId();
        Long seSuperpone = cargas.save(cargaCompatible()).getId();

        buscar(retorno("PUBLICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(seSuperpone.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(antesId.intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(despuesId.intValue()))));
    }

    @Test
    void laCarroceriaDeLaUnidadTieneQueEstarEntreLasAceptadas() throws Exception {
        Long acepta = cargas.save(cargaCompatible()).getId();
        Carga noAcepta = cargaCompatible();
        noAcepta.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.TANQUE));
        Long noAceptaId = cargas.save(noAcepta).getId();

        buscar(retorno("PUBLICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(acepta.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(noAceptaId.intValue()))));
    }

    @Test
    void elPesoYElVolumenTienenQueEntrarEnLaUnidad() throws Exception {
        Carga justa = cargaCompatible();
        justa.setPesoToneladas(new BigDecimal("28.00"));
        justa.setVolumenM3(new BigDecimal("90.00"));
        Long justaId = cargas.save(justa).getId();
        Carga pesada = cargaCompatible();
        pesada.setPesoToneladas(new BigDecimal("28.01"));
        Long pesadaId = cargas.save(pesada).getId();
        Carga voluminosa = cargaCompatible();
        voluminosa.setVolumenM3(new BigDecimal("90.01"));
        Long voluminosaId = cargas.save(voluminosa).getId();

        buscar(retorno("PUBLICADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(justaId.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(pesadaId.intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(voluminosaId.intValue()))));
    }

    @Test
    void unRetornoQueYaNoEstaPublicadoDevuelve409() throws Exception {
        buscar(retorno("VENCIDO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El retorno ya no está publicado (está VENCIDO)"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void unDadorNoPuedeBuscar() throws Exception {
        mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", "1")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinRetornoIdDevuelve400() throws Exception {
        mockMvc.perform(get("/api/cargas/de-paso").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isBadRequest());
    }

    private ResultActions buscar(Long retornoId) throws Exception {
        return mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", retornoId.toString())
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA));
    }

    /** Retorno de Rosario a Córdoba con una unidad SIDER de 28 t y 90 m³: sale en una ventana de 12 h. */
    private Long retorno(String estado) {
        Long id = RETORNOS.incrementAndGet();
        transporte.conRetorno(new RetornoInternoDTO(id, 3L, 12L, estado,
                new UbicacionDTO("Santa Fe", "Rosario", ROSARIO.lat(), ROSARIO.lng()),
                new UbicacionDTO("Córdoba", "Córdoba", CORDOBA.lat(), CORDOBA.lng()),
                SALIDA, SALIDA.plus(12, ChronoUnit.HOURS), 30.0, 401.5, 290.0,
                List.of(ROSARIO, VILLA_MARIA, CORDOBA), false, TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00")));
        return id;
    }

    /** Carga de Rosario a Villa María, que le queda de camino al retorno y entra en la unidad. */
    private static Carga cargaCompatible() {
        Carga carga = new Carga();
        carga.setDadorSub("sub-dador1");
        carga.setOrigen(new Ubicacion("Santa Fe", "Rosario", ROSARIO.lat(), ROSARIO.lng(), "Bv. Oroño 500"));
        carga.setDestino(new Ubicacion("Córdoba", "Villa María", VILLA_MARIA.lat(), VILLA_MARIA.lng(), "Sabattini 100"));
        carga.setCargaDesde(SALIDA);
        carga.setCargaHasta(SALIDA.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(SALIDA.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(SALIDA.plus(2, ChronoUnit.DAYS));
        carga.setRubro("Alimentos secos");
        carga.setPesoToneladas(new BigDecimal("12.50"));
        carga.setVolumenM3(new BigDecimal("40.00"));
        carga.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.SIDER, TipoCarroceria.FURGON));
        carga.setPrecioOfrecido(new BigDecimal("850000.00"));
        carga.setValorDeclarado(new BigDecimal("12000000.00"));
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        return carga;
    }
}
