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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/viajes/{id}/hitos con la cadena de seguridad real: el camino feliz y quién puede registrar hitos.
 * Los viajes salen del fixture, CONFIRMADOS como los deja FLY-26.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class RegistrarHitoTest {

    static final String COMPROBANTE = """
            {"numeroRemito": "R-0001-00012345", "recibioNombre": "Ana Gómez", "recibioDni": "30111222"}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void elTransportistaRecorreLosHitosEnOrden() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "CARGADO", TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CARGADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].estadoAnterior").value("CONFIRMADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].estadoNuevo").value("CARGADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].actorSub").value(ViajesDePrueba.SUB_TRANSPORTISTA));

        hito(id, "EN_TRANSITO", TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_TRANSITO"));

        entrega(id, COMPROBANTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.lineaDeTiempo.length()").value(4))
                .andExpect(jsonPath("$.lineaDeTiempo[3].detalle").value("Remito R-0001-00012345, recibió Ana Gómez"));
    }

    @Test
    void otroTransportistaRecibe403YElViajeNoCambia() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "CARGADO", TokensDePrueba.TERCERO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos el transportista de este viaje"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
    }

    @Test
    void elDadorNoRegistraHitos() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "CARGADO", TokensDePrueba.DADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
    }

    @Test
    void unViajeQueNoExisteDa404() throws Exception {
        hito(999_999L, "CARGADO", TokensDePrueba.TRANSPORTISTA).andExpect(status().isNotFound());
    }

    ResultActions hito(Long viajeId, String hito, String token) throws Exception {
        return enviar(viajeId, "{\"hito\": \"" + hito + "\"}", token);
    }

    ResultActions entrega(Long viajeId, String comprobante) throws Exception {
        return enviar(viajeId, "{\"hito\": \"ENTREGADO\", \"comprobante\": " + comprobante + "}",
                TokensDePrueba.TRANSPORTISTA);
    }

    ResultActions enviar(Long viajeId, String cuerpo, String token) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/hitos", viajeId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
