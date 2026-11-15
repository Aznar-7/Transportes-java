package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.EstadoCarga;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/viajes/{id}/conformidad con la cadena de seguridad real: el dador conforma dentro de la ventana y el
 * viaje queda CONFORMADO por DADOR, con su carga CERRADA. Y solo el dador de ese viaje puede hacerlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class ConformidadDelDadorTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void elDadorConformaDentroDeLaVentana() throws Exception {
        Long id = fixture.viajeEntregado();

        conformar(id, TokensDePrueba.DADOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFORMADO"))
                .andExpect(jsonPath("$.conformadoPor").value("DADOR"))
                .andExpect(jsonPath("$.lineaDeTiempo[4].estadoAnterior").value("ENTREGADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[4].estadoNuevo").value("CONFORMADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[4].actorSub").value(ViajesDePrueba.SUB_DADOR))
                .andExpect(jsonPath("$.lineaDeTiempo[4].detalle").value("Conformó el dador"));

        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.CERRADA);
    }

    @Test
    void otroDadorRecibe403YElViajeSigueEntregado() throws Exception {
        Long id = fixture.viajeEntregado();

        conformar(id, TokensDePrueba.OTRO_DADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos el dador de este viaje"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(fixture.estadoDeCarga(cargaDe(id))).isEqualTo(EstadoCarga.ASIGNADA);
    }

    @Test
    void elTransportistaNoConforma() throws Exception {
        Long id = fixture.viajeEntregado();

        conformar(id, TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
    }

    @Test
    void unViajeQueNoExisteDa404() throws Exception {
        conformar(999_999L, TokensDePrueba.DADOR).andExpect(status().isNotFound());
    }

    private Long cargaDe(Long viajeId) {
        Long cargaId = fixture.conViaje(viajeId, viaje -> viaje.getCarga().getId());
        return cargaId;
    }

    private ResultActions conformar(Long viajeId, String token) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/conformidad", viajeId).header(HttpHeaders.AUTHORIZATION, token));
    }
}
