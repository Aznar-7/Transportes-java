package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.EstadoCarga;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una vez cargada la mercadería ya no se puede cancelar: desde CARGADO en adelante, cualquiera de las partes
 * recibe 409 y no cambia nada (ni el viaje, ni su línea de tiempo, ni la carga).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class CancelarDespuesDeCargadoTest {

    /** Los hitos en orden, para llevar el viaje del fixture hasta el estado de cada caso. */
    private static final List<TipoEstadoViaje> HITOS =
            List.of(TipoEstadoViaje.CARGADO, TipoEstadoViaje.EN_TRANSITO, TipoEstadoViaje.ENTREGADO);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @ParameterizedTest(name = "{1} cancela un viaje {0}: 409")
    @CsvSource({
            "CARGADO, DADOR",
            "CARGADO, TRANSPORTISTA",
            "EN_TRANSITO, DADOR",
            "EN_TRANSITO, TRANSPORTISTA",
            "ENTREGADO, DADOR",
            "ENTREGADO, TRANSPORTISTA"
    })
    void desdeCargadoEnAdelanteNoSePuedeCancelar(TipoEstadoViaje estado, ParteDelViaje quien) throws Exception {
        Long id = viajeEn(estado);
        int eventosAntes = eventos(id);

        cancelar(id, quien == ParteDelViaje.DADOR ? TokensDePrueba.DADOR : TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto con el estado actual"))
                .andExpect(jsonPath("$.detail").value("El viaje está " + estado + ": no se puede cancelar"))
                .andExpect(jsonPath("$.estadoActual").value(estado.name()));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(estado);
        assertThat(fixture.conViaje(id, Viaje::getCanceladoEn)).isNull();
        assertThat(fixture.conViaje(id, Viaje::getConPenalidad)).isNull();
        assertThat(eventos(id)).isEqualTo(eventosAntes);
        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.ASIGNADA);
    }

    /** Tampoco se cancela dos veces: CANCELADO es final. */
    @Test
    void unViajeCanceladoNoSeVuelveACancelar() throws Exception {
        Long id = fixture.viajeConfirmado(ViajesDePrueba.unidadNueva(),
                Instant.now().plus(3, ChronoUnit.DAYS)).getId();
        cancelar(id, TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());

        cancelar(id, TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CANCELADO: no se puede cancelar"));

        // Quedó lo de la primera cancelación: la hizo el transportista y la carga volvió a PUBLICADA.
        assertThat(fixture.conViaje(id, Viaje::getCanceladoPor)).isEqualTo(ParteDelViaje.TRANSPORTISTA);
        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.PUBLICADA);
    }

    /** Un viaje del fixture llevado, hito por hito, hasta el estado pedido. */
    private Long viajeEn(TipoEstadoViaje estado) {
        Long id = fixture.viajeConfirmado().getId();
        for (TipoEstadoViaje hito : HITOS) {
            fixture.pasarA(id, hito);
            if (hito == estado) {
                return id;
            }
        }
        throw new IllegalArgumentException("No es un estado posterior a CARGADO: " + estado);
    }

    private int eventos(Long viajeId) {
        Integer cantidad = fixture.conViaje(viajeId, viaje -> viaje.getEventos().size());
        return cantidad;
    }

    private Long cargaDe(Long viajeId) {
        Long cargaId = fixture.conViaje(viajeId, viaje -> viaje.getCarga().getId());
        return cargaId;
    }

    private ResultActions cancelar(Long viajeId, String token) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/cancelacion", viajeId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Me arrepentí\"}"));
    }
}
