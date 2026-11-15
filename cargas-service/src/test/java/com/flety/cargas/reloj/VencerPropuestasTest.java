package com.flety.cargas.reloj;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
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
 * El Reloj de propuestas contra H2: la regla recibe la hora, así se prueba "dentro de 25 h" sin esperar.
 * La base se comparte con otros tests, así que se mira el estado de cada propuesta y no solo el total.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class VencerPropuestasTest {

    @Autowired
    VencimientosService vencimientos;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Test
    void unaPendienteVenceAlCumplirUnDia() {
        Propuesta propuesta = pendienteConLaCargaEnTresDias();

        int vencidas = vencimientos.vencerPropuestas(Instant.now().plus(Duration.ofHours(25)));

        assertThat(vencidas).isPositive();
        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.VENCIDA);
    }

    @Test
    void antesDeTiempoNoVence() {
        Propuesta propuesta = pendienteConLaCargaEnTresDias();

        vencimientos.vencerPropuestas(Instant.now());

        assertThat(fixture.estadoDePropuesta(propuesta.getId())).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    /** Solo vencen las PENDIENTES: una aceptada, rechazada o retirada ya tiene su final y no se pisa. */
    @Test
    void lasQueNoEstanPendientesNoSeTocan() {
        Propuesta aceptada = pendienteConLaCargaEnTresDias();
        Propuesta rechazada = pendienteConLaCargaEnTresDias();
        cambiarEstado(aceptada, EstadoPropuesta.ACEPTADA);
        cambiarEstado(rechazada, EstadoPropuesta.RECHAZADA);

        vencimientos.vencerPropuestas(Instant.now().plus(Duration.ofDays(10)));

        assertThat(fixture.estadoDePropuesta(aceptada.getId())).isEqualTo(EstadoPropuesta.ACEPTADA);
        assertThat(fixture.estadoDePropuesta(rechazada.getId())).isEqualTo(EstadoPropuesta.RECHAZADA);
    }

    private Propuesta pendienteConLaCargaEnTresDias() {
        Carga carga = fixture.cargaAbierta(Instant.now().plus(Duration.ofDays(3)));
        return fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
    }

    private void cambiarEstado(Propuesta propuesta, EstadoPropuesta estado) {
        fixture.tx().executeWithoutResult(t -> fixture.propuestas().findById(propuesta.getId()).orElseThrow()
                .setEstado(estado));
    }
}
