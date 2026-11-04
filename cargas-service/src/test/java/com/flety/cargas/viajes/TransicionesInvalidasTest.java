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
 * Lo que el ciclo de vida no permite, por la API: una transición que el estado no admite da 409 (y el viaje
 * queda como estaba), y una entrega sin comprobante da 400 (sin llegar a mirar el estado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class TransicionesInvalidasTest {

    private static final String COMPROBANTE = RegistrarHitoTest.COMPROBANTE;
    private static final String TITULO_409 = "Conflicto con el estado actual";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    ComprobanteEntregaRepository comprobantes;

    // ---- 409: el estado actual no permite la acción ----

    @Test
    void saltearUnHitoDa409YElViajeNoCambia() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "EN_TRANSITO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value(TITULO_409))
                .andExpect(jsonPath("$.detail").value("El viaje está CONFIRMADO: no se puede iniciar el tránsito"))
                .andExpect(jsonPath("$.estadoActual").value("CONFIRMADO"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
        assertThat(eventos(id)).isOne();
    }

    @Test
    void entregarDesdeConfirmadoDa409YNoGuardaElComprobante() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        entrega(id, COMPROBANTE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CONFIRMADO: no se puede registrar la entrega"));

        assertThat(comprobantes.findByViajeId(id)).isEmpty();
        assertThat(fixture.conViaje(id, Viaje::getEntregadoEn)).isNull();
    }

    @Test
    void repetirUnHitoDa409() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        hito(id, "CARGADO").andExpect(status().isOk());

        hito(id, "CARGADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CARGADO: no se puede cargar"));

        assertThat(eventos(id)).isEqualTo(2);
    }

    @Test
    void volverAtrasDa409() throws Exception {
        Long id = enTransito();

        hito(id, "CARGADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estadoActual").value("EN_TRANSITO"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_TRANSITO);
    }

    @Test
    void despuesDeEntregarNoHayMasHitos() throws Exception {
        Long id = enTransito();
        entrega(id, COMPROBANTE).andExpect(status().isOk());

        hito(id, "EN_TRANSITO").andExpect(status().isConflict());
        entrega(id, COMPROBANTE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está ENTREGADO: no se puede registrar la entrega"));
    }

    /** La excepción es una ReglaNegocioException, pero la API responde 409 y no el 422 de las demás reglas. */
    @Test
    void laTransicionNoPermitidaNoSaleComo422() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "EN_TRANSITO")
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value(TITULO_409));
    }

    // ---- 400: el pedido está incompleto ----

    @Test
    void entregarSinComprobanteDa400YElViajeSigueEnTransito() throws Exception {
        Long id = enTransito();

        hito(id, "ENTREGADO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errores.comprobanteSegunElHito")
                        .value("el comprobante es obligatorio con ENTREGADO y no va con los demás hitos"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_TRANSITO);
        assertThat(fixture.conViaje(id, Viaje::getVentanaConformidadHasta)).isNull();
        assertThat(comprobantes.findByViajeId(id)).isEmpty();
    }

    /** El 400 sale antes de mirar el estado: aunque el viaje esté CONFIRMADO, no llega a ser un 409. */
    @Test
    void sinComprobanteEs400AunqueElEstadoTampocoLoPermita() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        hito(id, "ENTREGADO").andExpect(status().isBadRequest());
    }

    @Test
    void unComprobanteIncompletoDa400ConCadaCampo() throws Exception {
        Long id = enTransito();

        entrega(id, """
                {"numeroRemito": " ", "recibioNombre": "Ana Gómez", "recibioDni": "30.111.222"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['comprobante.numeroRemito']").value("falta el número de remito"))
                .andExpect(jsonPath("$.errores['comprobante.recibioDni']")
                        .value("el DNI son 7 u 8 dígitos, sin puntos"));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_TRANSITO);
    }

    @Test
    void unComprobanteConOtroHitoDa400() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        enviar(id, "{\"hito\": \"CARGADO\", \"comprobante\": " + COMPROBANTE + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.comprobanteSegunElHito").exists());

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
    }

    @Test
    void sinHitoOConUnHitoQueNoExisteDa400() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        enviar(id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.hito").value("falta el hito: CARGADO, EN_TRANSITO o ENTREGADO"));
        // CONFORMADO es un estado del viaje, pero no un hito del transportista.
        hito(id, "CONFORMADO").andExpect(status().isBadRequest());
    }

    /** Cuántos renglones tiene la línea de tiempo del viaje. */
    private int eventos(Long viajeId) {
        Integer cantidad = fixture.conViaje(viajeId, viaje -> viaje.getEventos().size());
        return cantidad;
    }

    private Long enTransito() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        hito(id, "CARGADO").andExpect(status().isOk());
        hito(id, "EN_TRANSITO").andExpect(status().isOk());
        return id;
    }

    private ResultActions hito(Long viajeId, String hito) throws Exception {
        return enviar(viajeId, "{\"hito\": \"" + hito + "\"}");
    }

    private ResultActions entrega(Long viajeId, String comprobante) throws Exception {
        return enviar(viajeId, "{\"hito\": \"ENTREGADO\", \"comprobante\": " + comprobante + "}");
    }

    private ResultActions enviar(Long viajeId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/viajes/{id}/hitos", viajeId)
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
