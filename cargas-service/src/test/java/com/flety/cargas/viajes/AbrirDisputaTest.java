package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.EstadoCarga;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/viajes/{id}/disputas con la cadena de seguridad real: el dador reclama dentro de la ventana y el viaje
 * queda EN_DISPUTA, con la disputa visible en su detalle para las dos partes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class AbrirDisputaTest {

    private static final String RECLAMO = """
            {"motivo": "Faltante", "descripcion": "Llegaron 18 pallets de 20"}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void elDadorAbreLaDisputaDentroDeLaVentana() throws Exception {
        Long id = fixture.viajeEntregado();

        disputar(id, TokensDePrueba.DADOR, RECLAMO)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/viajes/" + id))
                .andExpect(jsonPath("$.estado").value("EN_DISPUTA"))
                .andExpect(jsonPath("$.disputa.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.disputa.motivo").value("Faltante"))
                .andExpect(jsonPath("$.disputa.descripcion").value("Llegaron 18 pallets de 20"))
                .andExpect(jsonPath("$.disputa.abiertaPorSub").value(ViajesDePrueba.SUB_DADOR))
                .andExpect(jsonPath("$.disputa.aFavorDelTransportista").value(nullValue()))
                .andExpect(jsonPath("$.lineaDeTiempo[4].estadoNuevo").value("EN_DISPUTA"))
                .andExpect(jsonPath("$.lineaDeTiempo[4].detalle").value("Disputa abierta: Faltante"));

        // La carga sigue ASIGNADA: el viaje todavía no terminó.
        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.ASIGNADA);
    }

    @Test
    void elTransportistaTambienVeLaDisputaEnElDetalle() throws Exception {
        Long id = fixture.viajeEntregado();
        disputar(id, TokensDePrueba.DADOR, RECLAMO).andExpect(status().isCreated());

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputa.motivo").value("Faltante"));
    }

    @Test
    void unViajeSinDisputaNoLaTrae() throws Exception {
        Long id = fixture.viajeEntregado();

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(jsonPath("$.disputa").value(nullValue()));
    }

    @Test
    void fueraDeLaVentanaDa409() throws Exception {
        Long id = fixture.viajeEntregado();
        fixture.vencerVentana(id, Duration.ofMinutes(1));

        disputar(id, TokensDePrueba.DADOR, RECLAMO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("la conformidad ya la da el sistema")));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.ENTREGADO);
    }

    @Test
    void antesDeLaEntregaYDosVecesDa409() throws Exception {
        Long confirmado = fixture.viajeConfirmado().getId();
        disputar(confirmado, TokensDePrueba.DADOR, RECLAMO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CONFIRMADO: no se puede abrir una disputa"));

        Long id = fixture.viajeEntregado();
        disputar(id, TokensDePrueba.DADOR, RECLAMO).andExpect(status().isCreated());
        disputar(id, TokensDePrueba.DADOR, RECLAMO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estadoActual").value("EN_DISPUTA"));
    }

    @Test
    void quienNoEsElDadorDelViajeNoReclama() throws Exception {
        Long id = fixture.viajeEntregado();

        disputar(id, TokensDePrueba.OTRO_DADOR, RECLAMO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos el dador de este viaje"));
        disputar(id, TokensDePrueba.TRANSPORTISTA, RECLAMO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.ENTREGADO);
    }

    @Test
    void sinMotivoDa400() throws Exception {
        Long id = fixture.viajeEntregado();

        disputar(id, TokensDePrueba.DADOR, "{\"motivo\": \" \", \"descripcion\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("falta el motivo de la disputa"));
    }

    private Long cargaDe(Long viajeId) {
        Long cargaId = fixture.conViaje(viajeId, viaje -> viaje.getCarga().getId());
        return cargaId;
    }

    private ResultActions disputar(Long viajeId, String token, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/disputas", viajeId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
