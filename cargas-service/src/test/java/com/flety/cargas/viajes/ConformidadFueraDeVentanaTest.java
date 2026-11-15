package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.EstadoCarga;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que no se puede conformar, por la API: con la ventana de 48 h vencida (le toca al sistema), antes de la entrega,
 * o un viaje que ya terminó. Siempre 409, y el viaje y su carga quedan como estaban.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class ConformidadFueraDeVentanaTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void conLaVentanaVencidaDa409YNoCambiaNada() throws Exception {
        Long id = fixture.viajeEntregado();
        fixture.vencerVentana(id, Duration.ofMinutes(1));

        conformar(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto con el estado actual"))
                .andExpect(jsonPath("$.detail").value(containsString("venció")))
                .andExpect(jsonPath("$.detail").value(containsString("la conformidad ya la da el sistema")));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(fixture.conViaje(id, Viaje::getConformadoPor)).isNull();
        assertThat(eventos(id)).isEqualTo(4);
        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.ASIGNADA);
    }

    /** El borde: un minuto antes de que venza, la ventana sigue abierta y el dador conforma. */
    @Test
    void unMinutoAntesDeVencerTodaviaConforma() throws Exception {
        Long id = fixture.viajeEntregado();
        fixture.vencerVentana(id, Duration.ofMinutes(-1));

        conformar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformadoPor").value("DADOR"));
    }

    @ParameterizedTest(name = "un viaje {0} no se conforma")
    @EnumSource(value = TipoEstadoViaje.class, names = {"CONFIRMADO", "CARGADO", "EN_TRANSITO"})
    void antesDeLaEntregaDa409(TipoEstadoViaje estado) throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        // Los hitos en orden hasta llegar al estado del caso (CONFIRMADO no necesita ninguno).
        List<TipoEstadoViaje> hitos = List.of(TipoEstadoViaje.CARGADO, TipoEstadoViaje.EN_TRANSITO);
        hitos.subList(0, hitos.indexOf(estado) + 1).forEach(hito -> fixture.pasarA(id, hito));

        conformar(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está " + estado + ": no se puede dar conformidad"))
                .andExpect(jsonPath("$.estadoActual").value(estado.name()));
    }

    @Test
    void conformarDosVecesDa409() throws Exception {
        Long id = fixture.viajeEntregado();
        conformar(id).andExpect(status().isOk());

        conformar(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CONFORMADO: no se puede dar conformidad"));

        assertThat(eventos(id)).isEqualTo(5);
    }

    @Test
    void unViajeCanceladoNoSeConforma() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        fixture.pasarA(id, TipoEstadoViaje.CANCELADO);

        conformar(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estadoActual").value("CANCELADO"));
    }

    private int eventos(Long viajeId) {
        Integer cantidad = fixture.conViaje(viajeId, viaje -> viaje.getEventos().size());
        return cantidad;
    }

    private Long cargaDe(Long viajeId) {
        Long cargaId = fixture.conViaje(viajeId, viaje -> viaje.getCarga().getId());
        return cargaId;
    }

    private ResultActions conformar(Long viajeId) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/conformidad", viajeId)
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR));
    }
}
