package com.flety.cargas.viajes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RN-15 contra la base: qué viajes cuentan como activos y con qué ventana. La base H2 se comparte entre las
 * clases de test, así que cada test usa su propia unidad para no ver los viajes de otro.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class ViajesActivosPorUnidadJpaTest {

    /** Lo que inyecta Spring: tiene que ser la implementación JPA, no el fake. */
    @Autowired
    ViajesActivosPorUnidad viajesActivos;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    private final Instant manana = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Test
    void springInyectaLaImplementacionSobreLaBase() {
        assertThat(viajesActivos).isInstanceOf(ViajesActivosPorUnidadJpa.class);
    }

    @Test
    void devuelveLosViajesActivosConLaVentanaDeLaCarga() {
        Viaje viaje = fixture.viajeConfirmado(501L, manana);

        List<ViajeActivo> activos = viajesActivos.buscar(501L);

        assertThat(activos).hasSize(1);
        ViajeActivo activo = activos.getFirst();
        assertThat(activo.viajeId()).isEqualTo(viaje.getId());
        assertThat(activo.cargaId()).isEqualTo(viaje.getCarga().getId());
        assertThat(activo.desde()).isEqualTo(manana);
        assertThat(activo.hasta()).isEqualTo(manana.plus(2, ChronoUnit.DAYS));
    }

    @Test
    void enCursoYEnDisputaSiguenActivos() {
        Long enTransito = fixture.viajeConfirmado(502L, manana).getId();
        fixture.pasarA(enTransito, TipoEstadoViaje.EN_TRANSITO);
        Long enDisputa = fixture.viajeConfirmado(502L, manana.plus(10, ChronoUnit.DAYS)).getId();
        fixture.pasarA(enDisputa, TipoEstadoViaje.EN_DISPUTA);

        assertThat(viajesActivos.buscar(502L)).extracting(ViajeActivo::viajeId)
                .containsExactly(enTransito, enDisputa);
    }

    @Test
    void losCanceladosYLosCerradosNoCuentan() {
        Long cancelado = fixture.viajeConfirmado(503L, manana).getId();
        fixture.pasarA(cancelado, TipoEstadoViaje.CANCELADO);
        Long conformado = fixture.viajeConfirmado(503L, manana.plus(5, ChronoUnit.DAYS)).getId();
        fixture.pasarA(conformado, TipoEstadoViaje.CONFORMADO);
        Long cerrado = fixture.viajeConfirmado(503L, manana.plus(10, ChronoUnit.DAYS)).getId();
        fixture.pasarA(cerrado, TipoEstadoViaje.CERRADO_CON_DISPUTA);

        assertThat(viajesActivos.buscar(503L)).isEmpty();
    }

    @Test
    void soloDevuelveLosDeEsaUnidad() {
        fixture.viajeConfirmado(504L, manana);

        assertThat(viajesActivos.buscar(505L)).isEmpty();
    }

    @Test
    void detectaSuperposicionConLaVentanaDeUnViajeActivo() {
        fixture.viajeConfirmado(506L, manana); // ocupa de mañana a mañana + 2 días

        // Una carga que empieza en el medio de esa ventana se pisa.
        assertThat(viajesActivos.haySuperposicion(506L,
                manana.plus(1, ChronoUnit.DAYS), manana.plus(4, ChronoUnit.DAYS))).isTrue();
        // Una que empieza justo cuando termina la otra no se pisa (intervalos semiabiertos).
        assertThat(viajesActivos.haySuperposicion(506L,
                manana.plus(2, ChronoUnit.DAYS), manana.plus(3, ChronoUnit.DAYS))).isFalse();
    }
}
