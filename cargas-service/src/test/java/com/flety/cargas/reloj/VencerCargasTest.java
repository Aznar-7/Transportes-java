package com.flety.cargas.reloj;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.viajes.ViajesDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Reloj de cargas contra H2 (RN-17): una carga sin viaje con la ventana cerrada queda VENCIDA; una con viaje o
 * con la ventana abierta, no. Se llama al service con la hora en vez de esperar a que el Reloj dispare.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class VencerCargasTest {

    @Autowired
    VencimientosService vencimientos;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void unaCargaConPropuestasYLaVentanaCerradaVence() {
        // Ventana de carga de hace 10 h a hace 2 h, y nadie la aceptó.
        Carga carga = fixture.cargaAbierta(Instant.now().minus(Duration.ofHours(10)));
        long versionAntes = version(carga.getId());

        int vencidas = vencimientos.vencerCargas(Instant.now());

        assertThat(vencidas).isPositive();
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.VENCIDA);
        // El UPDATE masivo suma la versión a mano, así un pedido que la leyó antes choca y no pisa el VENCIDA.
        assertThat(version(carga.getId())).isEqualTo(versionAntes + 1);
    }

    @Test
    void unaCargaPublicadaSinPropuestasTambienVence() {
        Carga carga = fixture.cargaAbierta(Instant.now().minus(Duration.ofHours(10)));
        fixture.tx().executeWithoutResult(t -> fixture.cargas().findById(carga.getId()).orElseThrow()
                .setEstado(EstadoCarga.PUBLICADA));

        vencimientos.vencerCargas(Instant.now());

        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.VENCIDA);
    }

    @Test
    void conLaVentanaAbiertaNoVence() {
        Carga carga = fixture.cargaAbierta(Instant.now().minus(Duration.ofHours(2)));

        vencimientos.vencerCargas(Instant.now());

        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    /** "Sin viaje": una carga ASIGNADA tiene viaje aunque su ventana de carga ya haya pasado, y no vence. */
    @Test
    void unaCargaConViajeNoVence() {
        Long cargaId = fixture.conViaje(fixture.viajeConfirmado(ViajesDePrueba.unidadNueva(),
                Instant.now().minus(Duration.ofHours(10))).getId(), viaje -> viaje.getCarga().getId());

        vencimientos.vencerCargas(Instant.now());

        assertThat(fixture.estadoDeCarga(cargaId)).isEqualTo(EstadoCarga.ASIGNADA);
    }

    private long version(Long cargaId) {
        return fixture.cargas().findById(cargaId).orElseThrow().getVersion();
    }
}
