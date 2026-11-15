package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoCarga;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La conformidad automática contra H2, llamando al service con la hora (como hace el Reloj) en vez de esperar a
 * que dispare. La base se comparte con otros tests, así que se mira el estado de cada viaje y no solo el total.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class ConformidadAutomaticaTest {

    @Autowired
    ConformidadAutomaticaService conformidad;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void conVentanaVencidaYSinAccionConformaElSistema() {
        Long id = fixture.viajeEntregado();
        fixture.vencerVentana(id, Duration.ofMinutes(5));

        int conformados = conformidad.conformarVencidas(Instant.now());

        assertThat(conformados).isPositive();
        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFORMADO);
        assertThat(fixture.conViaje(id, Viaje::getConformadoPor)).isEqualTo(ConformadoPor.SISTEMA);
        String actor = fixture.conViaje(id, viaje -> viaje.getEventos().getLast().getActorSub());
        EstadoCarga carga = fixture.conViaje(id, viaje -> viaje.getCarga().getEstado());
        assertThat(actor).isEqualTo(EventoViaje.ACTOR_SISTEMA);
        assertThat(carga).isEqualTo(EstadoCarga.CERRADA);
    }

    @Test
    void conLaVentanaAbiertaNoToca() {
        Long id = fixture.viajeEntregado();

        conformidad.conformarVencidas(Instant.now());

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(fixture.conViaje(id, Viaje::getConformadoPor)).isNull();
    }

    /** Si el dador ya conformó, el viaje no está ENTREGADO y el Reloj ni lo ve: no lo pisa como SISTEMA. */
    @Test
    void loQueYaConformoElDadorNoSeVuelveAConformar() {
        Long id = fixture.viajeEntregado();
        conformarComoDador(id);
        fixture.vencerVentana(id, Duration.ofMinutes(5));

        conformidad.conformarVencidas(Instant.now());

        assertThat(fixture.conViaje(id, Viaje::getConformadoPor)).isEqualTo(ConformadoPor.DADOR);
        Integer eventos = fixture.conViaje(id, viaje -> viaje.getEventos().size());
        assertThat(eventos).isEqualTo(5);
    }

    /** Un viaje que todavía no se entregó no tiene ventana y no entra en la búsqueda. */
    @Test
    void unViajeSinEntregarNoEntra() {
        Long id = fixture.viajeConfirmado(ViajesDePrueba.unidadNueva(), Instant.now().plus(Duration.ofDays(3))).getId();

        conformidad.conformarVencidas(Instant.now().plus(Duration.ofDays(30)));

        assertThat(fixture.conViaje(id, Viaje::getEstado)).isEqualTo(TipoEstadoViaje.CONFIRMADO);
    }

    private void conformarComoDador(Long id) {
        fixture.tx().executeWithoutResult(t -> fixture.viajes().findById(id).orElseThrow()
                .conformar(ConformadoPor.DADOR, ViajesDePrueba.SUB_DADOR));
    }
}
