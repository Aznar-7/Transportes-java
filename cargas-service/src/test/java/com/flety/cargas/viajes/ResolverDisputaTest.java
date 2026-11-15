package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.EstadoCarga;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El operador resuelve la disputa, con la cadena de seguridad real: a favor del transportista el viaje queda
 * CONFORMADO, y si no, CERRADO_CON_DISPUTA. En los dos casos la carga queda CERRADA y la disputa RESUELTA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class ResolverDisputaTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void aFavorDelTransportistaElViajeQuedaConformado() throws Exception {
        Long viaje = fixture.viajeEntregado();
        Long disputa = abrirDisputa(viaje);

        resolver(disputa, TokensDePrueba.OPERADOR, true, "El remito muestra los 20 pallets firmados")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"))
                .andExpect(jsonPath("$.aFavorDelTransportista").value(true))
                .andExpect(jsonPath("$.estadoViaje").value("CONFORMADO"))
                .andExpect(jsonPath("$.resolucion").value("El remito muestra los 20 pallets firmados"))
                .andExpect(jsonPath("$.resueltaEn").exists());

        assertThat(fixture.conViaje(viaje, Viaje::getConformadoPor)).isEqualTo(ConformadoPor.OPERADOR);
        assertThat(fixture.estadoDeCarga(cargaDe(viaje))).isEqualTo(EstadoCarga.CERRADA);
    }

    @Test
    void enContraDelTransportistaQuedaCerradoConDisputa() throws Exception {
        Long viaje = fixture.viajeEntregado();
        Long disputa = abrirDisputa(viaje);

        resolver(disputa, TokensDePrueba.OPERADOR, false, "Faltaban 2 pallets")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aFavorDelTransportista").value(false))
                .andExpect(jsonPath("$.estadoViaje").value("CERRADO_CON_DISPUTA"));

        assertThat(fixture.conViaje(viaje, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CERRADO_CON_DISPUTA);
        assertThat(fixture.conViaje(viaje, Viaje::getConformadoPor)).isNull();
        assertThat(fixture.estadoDeCarga(cargaDe(viaje))).isEqualTo(EstadoCarga.CERRADA);

        // Las partes ven cómo terminó en el detalle del viaje, con la resolución en la línea de tiempo.
        mockMvc.perform(get("/api/viajes/{id}", viaje).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(jsonPath("$.disputa.estado").value("RESUELTA"))
                .andExpect(jsonPath("$.lineaDeTiempo[5].estadoNuevo").value("CERRADO_CON_DISPUTA"))
                .andExpect(jsonPath("$.lineaDeTiempo[5].detalle")
                        .value("Disputa resuelta en contra del transportista: Faltaban 2 pallets"));
    }

    @Test
    void unaDisputaResueltaNoSeVuelveAResolver() throws Exception {
        Long disputa = abrirDisputa(fixture.viajeEntregado());
        resolver(disputa, TokensDePrueba.OPERADOR, false, "Faltaban 2 pallets").andExpect(status().isOk());

        resolver(disputa, TokensDePrueba.OPERADOR, true, "Me arrepentí")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El viaje está CERRADO_CON_DISPUTA: no se puede resolver una disputa"));
    }

    @Test
    void laBandejaListaLasAbiertasYMuestraElDetalle() throws Exception {
        Long abierta = abrirDisputa(fixture.viajeEntregado());
        Long resuelta = abrirDisputa(fixture.viajeEntregado());
        resolver(resuelta, TokensDePrueba.OPERADOR, true, "ok").andExpect(status().isOk());

        String abiertas = mockMvc.perform(get("/api/viajes/disputas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(abiertas, "$[*].id")).contains(abierta.intValue())
                .doesNotContain(resuelta.intValue());

        mockMvc.perform(get("/api/viajes/disputas?estado=RESUELTA").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/viajes/disputas/{id}", abierta).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("Faltante"))
                .andExpect(jsonPath("$.estadoViaje").value("EN_DISPUTA"));
    }

    @Test
    void soloUnOperadorResuelve() throws Exception {
        Long viaje = fixture.viajeEntregado();
        Long disputa = abrirDisputa(viaje);

        resolver(disputa, TokensDePrueba.DADOR, true, "x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
        resolver(disputa, TokensDePrueba.TRANSPORTISTA, true, "x").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/viajes/disputas").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isForbidden());

        assertThat(fixture.conViaje(viaje, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.EN_DISPUTA);
    }

    @Test
    void sinDecisionNiFundamentoDa400YUnaDisputaQueNoExisteDa404() throws Exception {
        Long disputa = abrirDisputa(fixture.viajeEntregado());

        mockMvc.perform(post("/api/viajes/disputas/{id}/resolucion", disputa)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.aFavorDelTransportista").exists())
                .andExpect(jsonPath("$.errores.resolucion").exists());

        resolver(999_999L, TokensDePrueba.OPERADOR, true, "x").andExpect(status().isNotFound());
    }

    /** Abre la disputa por la API del dador, como en la realidad, y devuelve su id. */
    private Long abrirDisputa(Long viajeId) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/viajes/{id}/disputas", viajeId)
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Faltante\", \"descripcion\": \"Llegaron 18 pallets de 20\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.disputa.id")).longValue();
    }

    private Long cargaDe(Long viajeId) {
        Long cargaId = fixture.conViaje(viajeId, viaje -> viaje.getCarga().getId());
        return cargaId;
    }

    private ResultActions resolver(Long disputaId, String token, boolean aFavor, String resolucion) throws Exception {
        return mockMvc.perform(post("/api/viajes/disputas/{id}/resolucion", disputaId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aFavorDelTransportista\": " + aFavor + ", \"resolucion\": \"" + resolucion + "\"}"));
    }
}
