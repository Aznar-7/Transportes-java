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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con N = 2 y tres candidatas, se gasta la API de rutas solo en las dos más cercanas en línea recta.
 * La ventana está más adelante que en CargasDePasoTest, así el resultado tiene solo las cargas de este test.
 */
@SpringBootTest(properties = "flety.busqueda.max-desvios-reales=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, TransporteDePrueba.class, DesvioDePrueba.class})
@Transactional
class MejoresNTest {

    private static final Instant SALIDA = Instant.now().plus(500, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada VILLA_MARIA = new Coordenada(-32.4075, -63.2402);
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CargaRepository cargas;

    @Autowired
    TransporteClientFake transporte;

    @Autowired
    DesvioDePrueba.Calculador desvios;

    @Test
    void soloLasMasCercanasGastanLaApiDeRutas() throws Exception {
        desvios.limpiar();
        Long enRuta = cargas.save(carga(ROSARIO)).getId();
        Long cerca = cargas.save(carga(new Coordenada(-32.90, -60.90))).getId();
        cargas.save(carga(new Coordenada(-33.10, -61.50)));
        transporte.conRetorno(new RetornoInternoDTO(5000L, 3L, 12L, "PUBLICADO",
                new UbicacionDTO("Santa Fe", "Rosario", ROSARIO.lat(), ROSARIO.lng()),
                new UbicacionDTO("Córdoba", "Córdoba", CORDOBA.lat(), CORDOBA.lng()),
                SALIDA, SALIDA.plus(12, ChronoUnit.HOURS), 30.0, 401.5, 290.0,
                List.of(ROSARIO, VILLA_MARIA, CORDOBA), false, TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00"), "sub-transportista1"));

        mockMvc.perform(get("/api/cargas/de-paso").param("retornoId", "5000")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].carga.id", containsInAnyOrder(enRuta.intValue(), cerca.intValue())));

        assertThat(desvios.llamadas()).isEqualTo(2);
    }

    private static Carga carga(Coordenada retiro) {
        Carga carga = new Carga();
        carga.setDadorSub("sub-dador1");
        carga.setOrigen(new Ubicacion("Santa Fe", "Retiro", retiro.lat(), retiro.lng(), "Calle 1"));
        carga.setDestino(new Ubicacion("Córdoba", "Villa María", VILLA_MARIA.lat(), VILLA_MARIA.lng(), "Sabattini 100"));
        carga.setCargaDesde(SALIDA);
        carga.setCargaHasta(SALIDA.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(SALIDA.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(SALIDA.plus(2, ChronoUnit.DAYS));
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
