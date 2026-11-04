package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoCarga;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de la cancelación, en memoria y sin Spring: la penalidad según cuánto falta para la carga, y el
 * estado en que queda la carga según quién cancela.
 */
class CancelacionDelViajeTest {

    private static final String DADOR = ViajesDePrueba.SUB_DADOR;
    private static final String TRANSPORTISTA = ViajesDePrueba.SUB_TRANSPORTISTA;

    @Test
    void conMenosDe24hParaLaCargaQuedaConPenalidad() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(23)));

        viaje.cancelar("Se suspendió la venta", DADOR);

        assertThat(viaje.getConPenalidad()).isTrue();
        assertThat(viaje.getEventos().getLast().getDetalle())
                .isEqualTo("Canceló el DADOR con penalidad: Se suspendió la venta");
    }

    @Test
    void conMasDe24hNoHayPenalidad() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(25)));

        viaje.cancelar("Se rompió el camión", TRANSPORTISTA);

        assertThat(viaje.getConPenalidad()).isFalse();
        assertThat(viaje.getEventos().getLast().getDetalle()).isEqualTo("Canceló el TRANSPORTISTA: Se rompió el camión");
    }

    /** La penalidad no depende de quién cancela: también el transportista la tiene si avisa tarde. */
    @Test
    void elTransportistaTambienQuedaConPenalidadSiCancelaTarde() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(2)));

        viaje.cancelar("Se rompió el camión", TRANSPORTISTA);

        assertThat(viaje.getConPenalidad()).isTrue();
        assertThat(viaje.getCanceladoPor()).isEqualTo(ParteDelViaje.TRANSPORTISTA);
    }

    /** Si la ventana de carga ya empezó y el viaje sigue CONFIRMADO, cancelar también es con penalidad. */
    @Test
    void conLaVentanaYaEmpezadaTambienHayPenalidad() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(-1)));

        viaje.cancelar("No llegó el camión", DADOR);

        assertThat(viaje.getConPenalidad()).isTrue();
    }

    // ---- El estado de la carga según quién cancela ----

    @Test
    void siCancelaElDadorLaCargaQuedaCancelada() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        viaje.cancelar("Se suspendió la venta", DADOR);

        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CANCELADA);
    }

    @Test
    void siCancelaElTransportistaConLaVentanaAbiertaLaCargaVuelveAPublicada() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        viaje.cancelar("Se rompió el camión", TRANSPORTISTA);

        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.PUBLICADA);
    }

    /** La ventana sigue abierta aunque ya haya empezado: vale hasta cargaHasta (cargaDesde + 8 h en el fixture). */
    @Test
    void laVentanaEmpezadaPeroNoCerradaSigueAbierta() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(-2)));

        viaje.cancelar("Se rompió el camión", TRANSPORTISTA);

        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.PUBLICADA);
        assertThat(viaje.getConPenalidad()).isTrue();
    }

    @Test
    void siCancelaElTransportistaConLaVentanaCerradaLaCargaVence() {
        // La ventana de carga fue de hace 10 h a hace 2 h.
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(-10)));

        viaje.cancelar("No llegué", TRANSPORTISTA);

        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.VENCIDA);
    }

    @Test
    void siCancelaElDadorConLaVentanaCerradaLaCargaIgualQuedaCancelada() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria(dentroDe(Duration.ofHours(-10)));

        viaje.cancelar("Ya no la necesito", DADOR);

        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CANCELADA);
    }

    @Test
    void mientrasNoSeCanceleNoHayDatosDeCancelacion() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        assertThat(viaje.getConPenalidad()).isNull();
        assertThat(viaje.getCanceladoPor()).isNull();
        assertThat(viaje.getCanceladoEn()).isNull();
    }

    private static Instant dentroDe(Duration falta) {
        return Instant.now().plus(falta);
    }
}
