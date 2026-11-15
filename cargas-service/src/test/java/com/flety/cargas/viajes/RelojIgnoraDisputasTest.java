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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El Reloj de conformidad no toca un viaje en disputa, aunque su ventana de 48 h haya vencido. El Reloj no sabe nada
 * de disputas: lo frena el estado del viaje. En cada caso corre junto a un viaje de control, sin disputa y con la
 * ventana vencida, que el Reloj sí conforma: así se ve que corrió y que solo dejó afuera al que estaba en disputa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class RelojIgnoraDisputasTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    ConformidadAutomaticaService reloj;

    @Autowired
    ViajeRepository viajes;

    @Test
    void unViajeEnDisputaConLaVentanaVencidaSigueEnDisputa() throws Exception {
        Long enDisputa = fixture.viajeEntregado();
        abrirDisputa(enDisputa);
        fixture.vencerVentana(enDisputa, Duration.ofHours(1));
        Long control = entregadoConVentanaVencida();
        int eventos = eventos(enDisputa);

        reloj.conformarVencidas(Instant.now());

        assertThat(fixture.conViaje(enDisputa, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_DISPUTA);
        assertThat(fixture.conViaje(enDisputa, Viaje::getConformadoPor)).isNull();
        assertThat(eventos(enDisputa)).isEqualTo(eventos);
        assertThat(fixture.conViaje(control, Viaje::getConformadoPor)).isEqualTo(ConformadoPor.SISTEMA);
    }

    /** El primer freno: la consulta del Reloj ni siquiera lo trae. */
    @Test
    void laConsultaDelRelojNoTraeViajesEnDisputa() throws Exception {
        Long enDisputa = fixture.viajeEntregado();
        abrirDisputa(enDisputa);
        fixture.vencerVentana(enDisputa, Duration.ofHours(1));
        Long control = entregadoConVentanaVencida();

        assertThat(viajes.idsConVentanaVencida(Instant.now())).contains(control).doesNotContain(enDisputa);
    }

    /** El segundo freno, por si algún día otra consulta lo trajera: el estado no deja conformarlo como SISTEMA. */
    @Test
    void aunqueElRelojLoIntentaraElEstadoNoLoDeja() throws Exception {
        Long enDisputa = fixture.viajeEntregado();
        abrirDisputa(enDisputa);
        fixture.vencerVentana(enDisputa, Duration.ofHours(1));

        assertThatThrownBy(() -> fixture.tx().executeWithoutResult(t ->
                        viajes.findById(enDisputa).orElseThrow().conformar(ConformadoPor.SISTEMA, EventoViaje.ACTOR_SISTEMA)))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(fixture.conViaje(enDisputa, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_DISPUTA);
    }

    /** Después de la disputa la decide el operador, aunque las 48 h hayan pasado hace rato: el Reloj no se mete. */
    @Test
    void laDecideElOperadorYNoElReloj() throws Exception {
        Long viaje = fixture.viajeEntregado();
        Long disputa = abrirDisputa(viaje);
        fixture.vencerVentana(viaje, Duration.ofDays(3));
        reloj.conformarVencidas(Instant.now());

        resolver(disputa, true);
        reloj.conformarVencidas(Instant.now());

        assertThat(fixture.conViaje(viaje, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFORMADO);
        assertThat(fixture.conViaje(viaje, Viaje::getConformadoPor)).isEqualTo(ConformadoPor.OPERADOR);
    }

    /** Un viaje CERRADO_CON_DISPUTA es final: el Reloj tampoco lo toca. */
    @Test
    void unViajeCerradoConDisputaNoSeConforma() throws Exception {
        Long viaje = fixture.viajeEntregado();
        Long disputa = abrirDisputa(viaje);
        resolver(disputa, false);
        fixture.vencerVentana(viaje, Duration.ofHours(1));

        reloj.conformarVencidas(Instant.now());

        assertThat(fixture.conViaje(viaje, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CERRADO_CON_DISPUTA);
        assertThat(fixture.conViaje(viaje, Viaje::getConformadoPor)).isNull();
    }

    private Long entregadoConVentanaVencida() {
        Long id = fixture.viajeEntregado();
        fixture.vencerVentana(id, Duration.ofHours(1));
        return id;
    }

    private int eventos(Long viajeId) {
        Integer cantidad = fixture.conViaje(viajeId, viaje -> viaje.getEventos().size());
        return cantidad;
    }

    private Long abrirDisputa(Long viajeId) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/viajes/{id}/disputas", viajeId)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Faltante\", \"descripcion\": \"Llegaron 18 pallets de 20\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.disputa.id")).longValue();
    }

    private void resolver(Long disputaId, boolean aFavor) throws Exception {
        mockMvc.perform(post("/api/viajes/disputas/{id}/resolucion", disputaId)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aFavorDelTransportista\": " + aFavor + ", \"resolucion\": \"Decisión del operador\"}"))
                .andExpect(status().isOk());
    }
}
