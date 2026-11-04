package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import com.flety.cargas.transporte.TransporteClientFake;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dos aceptaciones al mismo tiempo, de verdad: dos hilos que esperan en una barrera y largan el POST juntos.
 * Cada caso tiene que terminar con exactamente un 201 y un 409, y la base tiene que quedar coherente.
 * <p>
 * Qué frena a la segunda depende de cómo se intercalen los hilos: si llega después, la validación de estado; si
 * llegan juntas a la misma carga, el @Version de Carga (bloqueo optimista); si son dos cargas con la misma
 * unidad, el SELECT ... FOR UPDATE sobre sus propuestas (bloqueo pesimista). Por eso cada caso se repite:
 * el resultado tiene que ser el mismo en cualquier intercalado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class, TransporteDePrueba.class})
class AceptacionesSimultaneasTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    TransporteClientFake transporte;

    @RepeatedTest(5)
    void dosPropuestasDeLaMismaCarga_unaGanaYLaOtraRecibe409() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta a = propuestaHabilitada(carga, ViajesDePrueba.unidadNueva(), ViajesDePrueba.SUB_TRANSPORTISTA);
        Propuesta b = propuestaHabilitada(carga, ViajesDePrueba.unidadNueva(), ViajesDePrueba.SUB_TERCERO);

        List<Integer> estados = aLaVez(a.getId(), b.getId());

        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.ASIGNADA);
        assertThat(List.of(fixture.estadoDePropuesta(a.getId()), fixture.estadoDePropuesta(b.getId())))
                .containsExactlyInAnyOrder(EstadoPropuesta.ACEPTADA, EstadoPropuesta.RECHAZADA);
        assertThat(viajesDeLaCarga(carga.getId())).isOne();
    }

    @RepeatedTest(5)
    void laMismaPropuestaDosVeces_soloSeCreaUnViaje() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = propuestaHabilitada(carga, ViajesDePrueba.unidadNueva(), ViajesDePrueba.SUB_TRANSPORTISTA);

        // El doble clic: el mismo dador manda la misma aceptación dos veces.
        List<Integer> estados = aLaVez(propuesta.getId(), propuesta.getId());

        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.ACEPTADA);
        assertThat(viajesDeLaCarga(carga.getId())).isOne();
    }

    @RepeatedTest(5)
    void dosCargasConLaMismaUnidadEnVentanasQueSePisan_soloUnaSeConfirma() throws Exception {
        Long unidadId = ViajesDePrueba.unidadNueva();
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        Carga primera = fixture.cargaAbierta(manana);
        Carga segunda = fixture.cargaAbierta(manana.plus(1, ChronoUnit.DAYS));
        Propuesta a = propuestaHabilitada(primera, unidadId, ViajesDePrueba.SUB_TRANSPORTISTA);
        Propuesta b = propuestaHabilitada(segunda, unidadId, ViajesDePrueba.SUB_TRANSPORTISTA);

        // Son cargas distintas: el @Version de Carga no las frena. Lo que las ordena es el bloqueo pesimista.
        List<Integer> estados = aLaVez(a.getId(), b.getId());

        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(viajesActivosDeLaUnidad(unidadId)).isOne();
    }

    /** Los dos POST salen juntos desde dos hilos y se devuelven sus códigos HTTP. */
    private List<Integer> aLaVez(Long propuestaA, Long propuestaB) throws Exception {
        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> primero = hilos.submit(aceptarDespuesDe(largada, propuestaA));
            Future<Integer> segundo = hilos.submit(aceptarDespuesDe(largada, propuestaB));
            return List.of(primero.get(30, TimeUnit.SECONDS), segundo.get(30, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }
    }

    private Callable<Integer> aceptarDespuesDe(CyclicBarrier largada, Long propuestaId) {
        return () -> {
            largada.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/cargas/propuestas/{id}/aceptacion", propuestaId)
                            .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                    .andReturn().getResponse().getStatus();
        };
    }

    private Propuesta propuestaHabilitada(Carga carga, Long unidadId, String transportistaSub) {
        transporte.conHabilitacion(TransporteDePrueba.habilitada(unidadId));
        return fixture.propuestaPendiente(carga, unidadId, transportistaSub);
    }

    private long viajesDeLaCarga(Long cargaId) {
        return fixture.viajes().findAll().stream().filter(v -> v.getCarga().getId().equals(cargaId)).count();
    }

    private long viajesActivosDeLaUnidad(Long unidadId) {
        return fixture.viajes().findAll().stream()
                .filter(v -> v.getUnidadId().equals(unidadId) && v.getEstado().activo())
                .count();
    }
}
