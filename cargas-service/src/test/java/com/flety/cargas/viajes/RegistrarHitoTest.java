package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    ComprobanteEntregaRepository comprobantes;

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
    void laEntregaGuardaElComprobanteYAbreLaVentanaDe48h() throws Exception {
        Long id = enTransito();

        MvcResult resultado = entrega(id, COMPROBANTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformadoPor").value(nullValue()))
                .andReturn();

        Instant entregadoEn = Instant.parse(JsonPath.read(resultado.getResponse().getContentAsString(), "$.entregadoEn"));
        Instant ventana = Instant.parse(JsonPath.read(resultado.getResponse().getContentAsString(),
                "$.ventanaConformidadHasta"));
        assertThat(Duration.between(entregadoEn, ventana)).isEqualTo(Duration.ofHours(48));
        assertThat(entregadoEn).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));

        ComprobanteEntrega comprobante = comprobantes.findByViajeId(id).orElseThrow();
        assertThat(comprobante.getNumeroRemito()).isEqualTo("R-0001-00012345");
        assertThat(comprobante.getRecibioNombre()).isEqualTo("Ana Gómez");
        assertThat(comprobante.getRecibioDni()).isEqualTo("30111222");
    }

    @Test
    void antesDeEntregarNoHayVentanaNiComprobante() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "CARGADO", TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregadoEn").value(nullValue()))
                .andExpect(jsonPath("$.ventanaConformidadHasta").value(nullValue()));

        assertThat(comprobantes.findByViajeId(id)).isEmpty();
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

    /** Un viaje del fixture llevado hasta EN_TRANSITO por la API. */
    Long enTransito() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        hito(id, "CARGADO", TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());
        hito(id, "EN_TRANSITO", TokensDePrueba.TRANSPORTISTA).andExpect(status().isOk());
        return id;
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
