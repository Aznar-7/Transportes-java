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

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Control por dueño (RN-T1): tener el rol correcto no alcanza, hay que ser parte del viaje.
 * Los tres usuarios del fixture son el dador, el transportista y un tercero. El tercero tiene un rol válido
 * para el endpoint, así que el Gateway lo deja pasar y es cargas-service quien lo corta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class ViajeDeTercerosTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void unTransportistaQueNoEsParteRecibe403() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TERCERO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Sin permiso"))
                // El 403 no filtra nada del viaje: ni contactos ni línea de tiempo.
                .andExpect(jsonPath("$.contactos").doesNotExist())
                .andExpect(jsonPath("$.lineaDeTiempo").doesNotExist());
    }

    @Test
    void unDadorQueNoEsParteRecibe403() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_DADOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permiso"));
    }

    @Test
    void unTerceroNoVeElViajeEnSuLista() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TERCERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id.intValue()))));
    }

    @Test
    void unOperadorRecibe403PorRol() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        // Este lo corta @PreAuthorize antes de mirar el viaje: OPERADOR no es un rol de las partes.
        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasPartesSiguenPudiendoVerlo() throws Exception {
        Long id = fixture.viajeConfirmado().getId();

        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/viajes/{id}", id).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk());
    }
}
