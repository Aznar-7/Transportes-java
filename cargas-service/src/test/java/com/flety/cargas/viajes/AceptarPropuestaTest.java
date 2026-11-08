package com.flety.cargas.viajes;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.TransporteDePrueba;
import com.flety.cargas.transporte.TransporteClientFake;
import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.Dador;
import com.flety.cargas.mercado.DadorRepository;
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
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/cargas/propuestas/{id}/aceptacion con la cadena de seguridad real. Las cargas y propuestas salen
 * del fixture, así el test no depende de que mercado (Heron) ya publique cargas y propuestas, y la habilitación
 * de la unidad sale del fake de TransporteClient, así no hace falta transporte-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class, TransporteDePrueba.class})
class AceptarPropuestaTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    TransporteClientFake transporte;

    @Autowired
    DadorRepository dadores;

    @Test
    void creaElViajeConfirmadoAsignaLaCargaYRechazaLasDemas() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta elegida = propuestaHabilitada(carga);
        Propuesta otra = propuestaHabilitada(carga, ViajesDePrueba.SUB_TERCERO);

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
        Propuesta propuesta = propuestaHabilitada(carga);

        aceptar(propuesta.getId(), TokensDePrueba.OTRO_DADOR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos el dador de esta carga"));

        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    @Test
    void unTransportistaNoPuedeAceptar() throws Exception {
        Propuesta propuesta = propuestaHabilitada(fixture.cargaAbierta());

        aceptar(propuesta.getId(), TokensDePrueba.TRANSPORTISTA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu rol no te permite hacer esto"));
    }

    @Test
    void aceptarDosVecesLaMismaDa409() throws Exception {
        Propuesta propuesta = propuestaHabilitada(fixture.cargaAbierta());

        aceptar(propuesta.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya no está pendiente (está ACEPTADA)"));
    }

    @Test
    void unaRechazadaPorHaberAceptadoOtraDa409() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta primera = propuestaHabilitada(carga);
        Propuesta segunda = propuestaHabilitada(carga, ViajesDePrueba.SUB_TERCERO);

        aceptar(primera.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(segunda.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta ya no está pendiente (está RECHAZADA)"));
    }

    @Test
    void conRiesgoSinAceptarloDa422YAceptandoloPasa() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = propuestaHabilitada(carga);
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
        Propuesta propuesta = propuestaHabilitada(carga);
        propuesta.setVencimiento(Instant.now().minus(1, ChronoUnit.HOURS));
        fixture.propuestas().save(propuesta);

        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La propuesta venció"));
    }

    @Test
    void copiaEnElViajeElContactoDeLaOrganizacionDelDador() throws Exception {
        // Este test no hace rollback y otros esperan que dador2 no tenga organización: por eso se borra al final.
        Dador organizacion = dadores.save(
                new Dador("sub-dador2", "33693450239", "Molinos del Centro SA", "+5493515559876", true));
        try {
            Carga carga = ViajesDePrueba.nuevaCarga(Instant.now().plus(1, ChronoUnit.DAYS));
            carga.setDadorSub("sub-dador2");
            carga.setEstado(EstadoCarga.CON_PROPUESTAS);
            Propuesta propuesta = propuestaHabilitada(fixture.cargas().save(carga));

            aceptar(propuesta.getId(), TokensDePrueba.OTRO_DADOR)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contactos.dador.nombre").value("Molinos del Centro SA"))
                    .andExpect(jsonPath("$.contactos.dador.telefono").value("+5493515559876"));
        } finally {
            dadores.delete(organizacion);
        }
    }

    @Test
    void copiaEnElViajeLosDatosDelTransportistaQueDaTransporte() throws Exception {
        Propuesta propuesta = propuestaHabilitada(fixture.cargaAbierta());

        MvcResult resultado = aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactos.transportista.nombre").value("Transportes Pérez SRL"))
                .andExpect(jsonPath("$.contactos.transportista.telefono").value("+54 351 555-1234"))
                .andExpect(jsonPath("$.contactos.unidadDominio").value("AB123CD"))
                // El fixture crea la carga directo, sin organización del dador: no hay de dónde copiar su contacto.
                .andExpect(jsonPath("$.contactos.dador").value(nullValue()))
                .andReturn();

        Long viajeId = idDelViaje(resultado);
        String guardado = fixture.conViaje(viajeId, v -> v.getContactoTransportista().getNombre());
        assertThat(guardado).isEqualTo("Transportes Pérez SRL");
    }

    @Test
    void conLaUnidadYaNoHabilitadaLaPropuestaQuedaInvalidada() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Long unidadId = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(TransporteDePrueba.noHabilitada(unidadId, "SEGURO_VENCIDO", "VTV_VENCIDA"));
        Propuesta propuesta = fixture.propuestaPendiente(carga, unidadId);
        Propuesta otra = propuestaHabilitada(carga, ViajesDePrueba.SUB_TERCERO);

        aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("SEGURO_VENCIDO, VTV_VENCIDA")));

        // La invalidación quedó guardada aunque la respuesta fue un error.
        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.INVALIDADA);
        // El resto no se tocó: la carga sigue abierta y el dador puede elegir la otra.
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.CON_PROPUESTAS);
        assertThat(fixture.estadoDePropuesta(otra.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
        aceptar(otra.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
    }

    @Test
    void laMismaUnidadNoPuedeQuedarEnDosViajesQueSePisan() throws Exception {
        Long unidadId = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(TransporteDePrueba.habilitada(unidadId));
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        // La primera ocupa la unidad de mañana a mañana + 2 días; la segunda empieza a mitad de esa ventana.
        Propuesta primera = fixture.propuestaPendiente(fixture.cargaAbierta(manana), unidadId);
        Propuesta segunda = fixture.propuestaPendiente(fixture.cargaAbierta(manana.plus(1, ChronoUnit.DAYS)), unidadId);

        aceptar(primera.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(segunda.getId(), TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La unidad ya tiene un viaje que se superpone con esta carga (RN-15)"));

        assertThat(fixture.estadoDePropuesta(segunda.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    void laMismaUnidadSiPuedeHacerViajesQueNoSePisan() throws Exception {
        Long unidadId = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(TransporteDePrueba.habilitada(unidadId));
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        Propuesta primera = fixture.propuestaPendiente(fixture.cargaAbierta(manana), unidadId);
        // Empieza justo cuando termina la descarga de la primera.
        Propuesta segunda = fixture.propuestaPendiente(fixture.cargaAbierta(manana.plus(2, ChronoUnit.DAYS)), unidadId);
        segunda.setVencimiento(manana.plus(2, ChronoUnit.DAYS));
        fixture.propuestas().save(segunda);

        aceptar(primera.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
        aceptar(segunda.getId(), TokensDePrueba.DADOR).andExpect(status().isCreated());
    }

    @Test
    void conTransporteCaidoDa503YNoConfirmaNada() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta propuesta = propuestaHabilitada(carga);
        transporte.simularCaida(true);
        try {
            aceptar(propuesta.getId(), TokensDePrueba.DADOR)
                    .andExpect(status().isServiceUnavailable());
        } finally {
            transporte.simularCaida(false);
        }

        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    @Test
    void unaPropuestaQueNoExisteDa404() throws Exception {
        aceptar(999_999L, TokensDePrueba.DADOR)
                .andExpect(status().isNotFound());
    }

    /** Propuesta pendiente con una unidad propia, que el fake de transporte informa como habilitada. */
    Propuesta propuestaHabilitada(Carga carga, String transportistaSub) {
        Long unidadId = ViajesDePrueba.unidadNueva();
        transporte.conHabilitacion(TransporteDePrueba.habilitada(unidadId));
        return fixture.propuestaPendiente(carga, unidadId, transportistaSub);
    }

    Propuesta propuestaHabilitada(Carga carga) {
        return propuestaHabilitada(carga, ViajesDePrueba.SUB_TRANSPORTISTA);
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
