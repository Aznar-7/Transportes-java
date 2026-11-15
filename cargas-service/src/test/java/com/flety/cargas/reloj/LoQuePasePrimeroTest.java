package com.flety.cargas.reloj;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
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
 * RN-16: una propuesta PENDIENTE vence a las 24 h o al cerrar la ventana de carga, lo que pase primero. Cada caso
 * mira justo antes y justo después del límite que corresponde.
 * <p>
 * Los límites se leen de la base y no del objeto en memoria: Instant.now() trae nanosegundos y la columna guarda
 * microsegundos redondeando, así que el valor guardado puede quedar unos nanosegundos después del de memoria. El
 * Reloj compara contra lo guardado, y el test también.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class LoQuePasePrimeroTest {

    @Autowired
    VencimientosService vencimientos;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    /** La ventana cierra en 10 h, antes de que se cumpla el día: vence al cerrar la ventana. */
    @Test
    void siLaVentanaCierraAntesVenceAlCerrarLaVentana() {
        Carga carga = fixture.cargaAbierta(Instant.now().plus(Duration.ofHours(2)));
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Instant cierre = cargaHastaGuardado(carga);

        vencimientos.vencerPropuestas(cierre.minusSeconds(1));
        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.PENDIENTE);

        vencimientos.vencerPropuestas(cierre);
        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.VENCIDA);
    }

    /** La ventana cierra en tres días: el día se cumple primero y vence a las 24 h. */
    @Test
    void siElDiaSeCumplePrimeroVenceALas24h() {
        Carga carga = fixture.cargaAbierta(Instant.now().plus(Duration.ofDays(3)));
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Instant unDia = creadaEnGuardado(propuesta).plus(Duration.ofHours(24));

        vencimientos.vencerPropuestas(unDia.minusSeconds(1));
        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.PENDIENTE);

        vencimientos.vencerPropuestas(unDia);
        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.VENCIDA);
    }

    /**
     * El Reloj aplica la regla con los datos de la entidad (creadaEn y la ventana de la carga), no solo con el campo
     * vencimiento que calcula mercado al crearla: aunque ese campo dijera "dentro de un mes", a las 24 h vence igual.
     */
    @Test
    void noDependeDelVencimientoGuardado() {
        Carga carga = fixture.cargaAbierta(Instant.now().plus(Duration.ofDays(3)));
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        fixture.tx().executeWithoutResult(t -> fixture.propuestas().findById(propuesta.getId()).orElseThrow()
                .setVencimiento(Instant.now().plus(Duration.ofDays(30))));

        vencimientos.vencerPropuestas(creadaEnGuardado(propuesta).plus(Duration.ofHours(24)));

        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.VENCIDA);
    }

    /**
     * La tarea de cargas vence primero las propuestas con la misma hora: una carga que vence no deja propuestas
     * PENDIENTES colgadas.
     */
    @Test
    void unaCargaQueVenceNoDejaPropuestasPendientes() {
        Carga carga = fixture.cargaAbierta(Instant.now().plus(Duration.ofHours(1)));
        Propuesta propuesta = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Instant despuesDelCierre = cargaHastaGuardado(carga).plusSeconds(1);

        vencimientos.vencerPropuestas(despuesDelCierre);
        vencimientos.vencerCargas(despuesDelCierre);

        assertThat(estado(propuesta)).isEqualTo(EstadoPropuesta.VENCIDA);
        assertThat(fixture.estadoDeCarga(carga.getId())).isEqualTo(EstadoCarga.VENCIDA);
    }

    private Instant creadaEnGuardado(Propuesta propuesta) {
        return fixture.propuestas().findById(propuesta.getId()).orElseThrow().getCreadaEn();
    }

    private Instant cargaHastaGuardado(Carga carga) {
        return fixture.cargas().findById(carga.getId()).orElseThrow().getCargaHasta();
    }

    private EstadoPropuesta estado(Propuesta propuesta) {
        return fixture.estadoDePropuesta(propuesta.getId());
    }
}
