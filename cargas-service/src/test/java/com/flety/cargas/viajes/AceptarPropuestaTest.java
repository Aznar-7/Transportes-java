package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/cargas/propuestas/{id}/aceptacion con la cadena de seguridad real. Las cargas y propuestas salen
 * del fixture, así el test no depende de que mercado (Heron) ya publique cargas y propuestas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
class AceptarPropuestaTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void creaElViajeConfirmadoAsignaLaCargaYRechazaLasDemas() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta elegida = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Propuesta otra = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva(), ViajesDePrueba.SUB_TERCERO);

        MvcResult resultado = aceptar(elegida.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/viajes/")))
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.propuestaId").value(elegida.getId()))
                .andExpect(jsonPath("$.precioAcordado").value(800000.00))
                .andExpect(jsonPath("$.lineaDeTiempo[0].estadoNuevo").value("CONFIRMADO"))
                .andExpect(jsonPath("$.lineaDeTiempo[0].actorSub").value(ViajesDePrueba.SUB_DADOR))
                .andReturn();

        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.ASIGNADA);
        assertThat(fixture.estadoDePropuesta(elegida.getId())).isEqualTo(EstadoPropuesta.ACEPTADA);
        assertThat(fixture.estadoDePropuesta(otra.getId())).isEqualTo(EstadoPropuesta.RECHAZADA);

        Long viajeId = idDelViaje(resultado);
        assertThat(fixture.conViaje(viajeId, Viaje::getTransportistaSub)).isEqualTo(ViajesDePrueba.SUB_TRANSPORTISTA);
        assertThat(fixture.conViaje(viajeId, Viaje::getUnidadId)).isEqualTo(elegida.getUnidadId());
    }

    @Test
    void otroDadorRecibe403YNoCambiaNada() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());

        aceptar(propuesta.getId(), TokensDePrueba.OTRO_DADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos el dador de esta carga"));

        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    @Test
    void unTransportistaNoPuedeAceptar() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());

        aceptar(propuesta.getId(), TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
    }

    @Test
    void aceptarDosVecesLaMismaDa409() throws Exception {
        Propuesta propuesta = fixture.propuestaPendiente(fixture.cargaAbierta(), ViajesDePrueba.unidadNueva());

        aceptar(propuesta.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya no está pendiente (está ACEPTADA)"));
    }

    @Test
    void unaRechazadaPorHaberAceptadoOtraDa409() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta primera = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Propuesta segunda = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva(), ViajesDePrueba.SUB_TERCERO);

        aceptar(primera.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(segunda.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya no está pendiente (está RECHAZADA)"));
    }

    @Test
    void conRiesgoSinAceptarloDa422YAceptandoloPasa() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        propuesta.setRequiereAceptacionRiesgo(true);
        fixture.propuestas().save(propuesta);

        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"));
        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);

        mockMvc.perform(post("/api/cargas/propuestas/{id}/aceptacion", propuesta.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptoRiesgo\": true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unaPropuestaVencidaDa409() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        propuesta.setVencimiento(Instant.now().minus(1, ChronoUnit.HOURS));
        fixture.propuestas().save(propuesta);

        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta venció"));
    }

    @Test
    void unaPropuestaQueNoExisteDa404() throws Exception {
        aceptar(999_999L, TokensDePrueba.DADOR)
                .andExpect(status().isNotFound());
    }

    org.springframework.test.web.servlet.ResultActions aceptar(Long propuestaId, String token) throws Exception {
        return mockMvc.perform(post("/api/cargas/propuestas/{id}/aceptacion", propuestaId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    static Long idDelViaje(MvcResult resultado) {
        String location = resultado.getResponse().getHeader(HttpHeaders.LOCATION);
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
