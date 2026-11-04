package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.Propuesta;
import com.flety.cargas.transporte.TransporteClientFake;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La cancelación cambia dos entidades en la misma transacción: el viaje y su carga. Acá se mira, por la API y en
 * la base, en qué estado queda la carga según quién cancela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class, TransporteDePrueba.class})
class EstadoDeLaCargaAlCancelarTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    TransporteClientFake transporte;

    @Test
    void siCancelaElDadorLaCargaQuedaCancelada() throws Exception {
        Viaje viaje = viajeConCargaEn(72);

        cancelar(viaje.getId(), TokensDePrueba.DADOR).andExpect(status().isOk());

        assertThat(fixture.estadoDeCarga(viaje.getCarga().getId())).isEqualTo(EstadoCarga.CANCELADA);
    }

    @Test
    void siCancelaElTransportistaConLaVentanaAbiertaLaCargaVuelveAPublicada() throws Exception {
        Viaje viaje = viajeConCargaEn(72);

        cancelar(viaje.getId(), TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());

        assertThat(fixture.estadoDeCarga(viaje.getCarga().getId())).isEqualTo(EstadoCarga.PUBLICADA);
    }

    @Test
    void siCancelaElTransportistaConLaVentanaCerradaLaCargaVence() throws Exception {
        // Ventana de carga de hace 10 h a hace 2 h, y el viaje seguía CONFIRMADO.
        Viaje viaje = viajeConCargaEn(-10);

        cancelar(viaje.getId(), TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());

        assertThat(fixture.estadoDeCarga(viaje.getCarga().getId())).isEqualTo(EstadoCarga.VENCIDA);
    }

    /**
     * Para qué sirve volver a PUBLICADA: la carga recibe una propuesta nueva de otro transportista y el dador la
     * acepta. Nace un segundo viaje para la misma carga; el índice único parcial lo permite porque el primero
     * está CANCELADO.
     */
    @Test
    void laCargaRepublicadaPuedeTenerOtroViaje() throws Exception {
        Viaje cancelado = viajeConCargaEn(72);
        cancelar(cancelado.getId(), TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());

        Carga carga = fixture.cargas().findById(cancelado.getCarga().getId()).orElseThrow();
        Long unidad = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(TransporteDePrueba.habilitada(unidad));
        Propuesta nueva = fixture.propuestaPendiente(carga, unidad, ViajesDePrueba.SUB_TERCERO);

        mockMvc.perform(post("/api/cargas/propuestas/{id}/aceptacion", nueva.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.cargaId").value(carga.getId()));

        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.ASIGNADA);
        assertThat(fixture.conViaje(cancelado.getId(), Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CANCELADO);
    }

    /** Si la cancelación no se permite, tampoco cambia la carga: las dos entidades van juntas. */
    @Test
    void siLaCancelacionFallaLaCargaNoCambia() throws Exception {
        Viaje viaje = viajeConCargaEn(72);
        fixture.pasarA(viaje.getId(), TipoEstadoViaje.CARGADO);

        cancelar(viaje.getId(), TokensDePrueba.DADOR).andExpect(status().isConflict());

        assertThat(fixture.estadoDeCarga(viaje.getCarga().getId())).isEqualTo(EstadoCarga.ASIGNADA);
    }

    Viaje viajeConCargaEn(long horas) {
        return fixture.viajeConfirmado(ViajesDePrueba.unidadNueva(), Instant.now().plus(horas, ChronoUnit.HOURS));
    }

    ResultActions cancelar(Long viajeId, String token) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/cancelacion", viajeId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Se rompió el camión\"}"));
    }
}
