package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/viajes/{id}/cancelacion con la cadena de seguridad real: quién puede cancelar y la penalidad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class CancelarViajeTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void elDadorCancelaSinPenalidadConTiempo() throws Exception {
        Long id = viajeConCargaEn(72);

        cancelar(id, TokensDePrueba.DADOR, "Se suspendió la venta")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.cancelacion.canceladoPor").value("DADOR"))
                .andExpect(jsonPath("$.cancelacion.motivo").value("Se suspendió la venta"))
                .andExpect(jsonPath("$.cancelacion.conPenalidad").value(false))
                .andExpect(jsonPath("$.cancelacion.canceladoEn").exists())
                .andExpect(jsonPath("$.lineaDeTiempo[1].estadoNuevo").value("CANCELADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].actorSub").value(ViajesDePrueba.SUB_DADOR))
                // RN-13: sin acuerdo ya no se muestran contactos ni direcciones exactas.
                .andExpect(jsonPath("$.contactos").value(nullValue()))
                .andExpect(jsonPath("$.origen.direccion").value(nullValue()));
    }

    @Test
    void elTransportistaCancelaConMenosDe24hYQuedaConPenalidad() throws Exception {
        Long id = viajeConCargaEn(12);

        cancelar(id, TokensDePrueba.TRANSPORTISTA, "Se rompió el camión")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion.canceladoPor").value("TRANSPORTISTA"))
                .andExpect(jsonPath("$.cancelacion.conPenalidad").value(true))
                .andExpect(jsonPath("$.lineaDeTiempo[1].detalle")
                        .value("Canceló el TRANSPORTISTA con penalidad: Se rompió el camión"));

        assertThat(fixture.conViaje(id, Viaje::getConPenalidad)).isTrue();
    }

    @Test
    void unViajeEnPieNoTraeCancelacion() throws Exception {
        Long id = viajeConCargaEn(72);

        mockMvc.perform(get("/api/viajes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion").value(nullValue()));
    }

    @Test
    void quienNoEsParteRecibe403YElViajeSigueEnPie() throws Exception {
        Long id = viajeConCargaEn(72);

        cancelar(id, TokensDePrueba.TERCERO, "No me gusta")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos parte de este viaje"));
        cancelar(id, TokensDePrueba.OTRO_DADOR, "No me gusta").andExpect(status().isForbidden());

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
    }

    @Test
    void unOperadorNoCancelaViajes() throws Exception {
        Long id = viajeConCargaEn(72);

        cancelar(id, TokensDePrueba.OPERADOR, "motivo")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
    }

    @Test
    void sinMotivoDa400() throws Exception {
        Long id = viajeConCargaEn(72);

        cancelar(id, TokensDePrueba.DADOR, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("falta el motivo de la cancelación"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
    }

    @Test
    void unViajeQueNoExisteDa404() throws Exception {
        cancelar(999_999L, TokensDePrueba.DADOR, "motivo").andExpect(status().isNotFound());
    }

    /** Viaje CONFIRMADO del fixture con la carga dentro de esas horas (y una unidad propia, para no chocar). */
    Long viajeConCargaEn(long horas) {
        return fixture.viajeConfirmado(ViajesDePrueba.unidadNueva(), Instant.now().plus(horas, ChronoUnit.HOURS))
                .getId();
    }

    ResultActions cancelar(Long viajeId, String token, String motivo) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/cancelacion", viajeId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"" + motivo + "\"}"));
    }
}
