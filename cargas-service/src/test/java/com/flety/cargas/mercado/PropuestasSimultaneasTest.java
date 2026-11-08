package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.transporte.TransporteClientFake;
import com.flety.cargas.viajes.ViajesDePrueba;
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

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Un doble clic: dos pedidos iguales al mismo tiempo. Sin transacción de test a propósito, para que cada pedido tenga
 * la suya y compitan de verdad por el bloqueo de la carga.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, TransporteDePrueba.class, ViajesDePrueba.class})
class PropuestasSimultaneasTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CargaRepository cargas;

    @Autowired
    PropuestaRepository propuestas;

    @Autowired
    TransporteClientFake transporte;

    @Test
    void dosPropuestasIgualesAlMismoTiempo_unaEntraYLaOtraRecibe409() throws Exception {
        Long cargaId = cargas.save(PropuestaControllerTest.cargaAbierta()).getId();
        Long unidad = ViajesDePrueba.unidadNueva();
        Long retornoId = 3000L + unidad;
        transporte.conHabilitacion(PropuestaControllerTest.habilitacion(unidad, "sub-transportista1", true, TipoCarroceria.SIDER));
        transporte.conRetorno(new RetornoInternoDTO(retornoId, 3L, unidad, "PUBLICADO",
                new UbicacionDTO("Santa Fe", "Rosario", -32.9468, -60.6393),
                new UbicacionDTO("Córdoba", "Córdoba", -31.4201, -64.1888),
                PropuestaControllerTest.CARGA_DESDE, PropuestaControllerTest.CARGA_DESDE.plus(12, ChronoUnit.HOURS),
                30.0, 401.5, 290.0, List.of(), false, TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00"), "sub-transportista1"));

        CountDownLatch largada = new CountDownLatch(1);
        Callable<Integer> pedido = () -> {
            largada.await();
            return mockMvc.perform(post("/api/cargas/" + cargaId + "/propuestas")
                            .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PropuestaControllerTest.aceptaPrecio(unidad, retornoId)))
                    .andReturn().getResponse().getStatus();
        };

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> uno = hilos.submit(pedido);
            Future<Integer> otro = hilos.submit(pedido);
            largada.countDown();

            assertThat(List.of(uno.get(30, TimeUnit.SECONDS), otro.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(propuestas.findByCargaIdOrderByCreadaEnAscIdAsc(cargaId)).hasSize(1);
    }
}
