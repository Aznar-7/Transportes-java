package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/viajes y /api/viajes/{id} con la cadena de seguridad real y tokens fijos (TokensDePrueba).
 * Los viajes se crean con el fixture, como los dejará FLY-26.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class ViajeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void elDadorVeElViajeConContactosYLineaDeTiempo() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        fixture.pasarA(id, TipoEstadoViaje.CARGADO);

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CARGADO"))
                .andExpect(jsonPath("$.miRol").value("DADOR"))
                .andExpect(jsonPath("$.contactos.transportista.nombre").value("Transportes Pérez SRL"))
                .andExpect(jsonPath("$.contactos.transportista.telefono").value("+54 351 555-1234"))
                .andExpect(jsonPath("$.contactos.unidadDominio").value("AB123CD"))
                .andExpect(jsonPath("$.origen.direccion").value("Av. Colón 1000"))
                .andExpect(jsonPath("$.lineaDeTiempo.length()").value(2))
                .andExpect(jsonPath("$.lineaDeTiempo[0].estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$.lineaDeTiempo[0].estadoNuevo").value("CONFIRMADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].estadoAnterior").value("CONFIRMADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[1].estadoNuevo").value("CARGADO"));
    }

    @Test
    void elTransportistaVeElMismoViajeConElContactoDelDador() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRol").value("TRANSPORTISTA"))
                .andExpect(jsonPath("$.contactos.dador.nombre").value("Molinos del Centro SA"))
                .andExpect(jsonPath("$.contactos.dador.telefono").value("+54 351 555-9876"))
                .andExpect(jsonPath("$.precioAcordado").value(800000.00));
    }

    @Test
    void cancelado_yaNoMuestraContactosNiDireccion() throws Exception {
        Long id = fixture.viajeConfirmado().getId();
        fixture.pasarA(id, TipoEstadoViaje.CANCELADO);

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactos").value(nullValue()))
                .andExpect(jsonPath("$.origen.direccion").value(nullValue()))
                .andExpect(jsonPath("$.origen.localidad").value("Córdoba"));
    }

    @Test
    void laListaTraeLosViajesDeQuienConsulta() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id.intValue())))
                .andExpect(jsonPath("$[0].miRol").value("TRANSPORTISTA"))
                .andExpect(jsonPath("$[0].origenLocalidad").value("Córdoba"));
    }

    @Test
    void unViajeQueNoExisteDa404() throws Exception {
        mockMvc.perform(get("/api/viajes/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void sinTokenDa401() throws Exception {
        mockMvc.perform(get("/api/viajes"))
                .andExpect(status().isUnauthorized());
    }
}
