package com.flety.cargas.viajes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * El Viaje delega cada acción en el objeto de su estado actual, en memoria y sin Spring.
 */
class ViajeDelegaEnSuEstadoTest {

    private static final String TRANSPORTISTA = ViajesDePrueba.SUB_TRANSPORTISTA;

    @Test
    void recorreLosHitosEnOrdenYDejaUnEventoPorCada() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        viaje.cargar(TRANSPORTISTA);
        viaje.iniciarTransito(TRANSPORTISTA);
        viaje.registrarEntrega(new DatosEntrega("R-0001-00012345", "Ana Gómez", "30111222"), TRANSPORTISTA);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(viaje.getEventos())
                .extracting(EventoViaje::getEstadoAnterior, EventoViaje::getEstadoNuevo, EventoViaje::getActorSub)
                .containsExactly(
                        tuple(null, TipoEstadoViaje.CONFIRMADO, ViajesDePrueba.SUB_DADOR),
                        tuple(TipoEstadoViaje.CONFIRMADO, TipoEstadoViaje.CARGADO, TRANSPORTISTA),
                        tuple(TipoEstadoViaje.CARGADO, TipoEstadoViaje.EN_TRANSITO, TRANSPORTISTA),
                        tuple(TipoEstadoViaje.EN_TRANSITO, TipoEstadoViaje.ENTREGADO, TRANSPORTISTA));
    }

    @Test
    void unaAccionNoPermitidaNoCambiaElEstadoNiDejaEvento() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        assertThatThrownBy(() -> viaje.iniciarTransito(TRANSPORTISTA))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está CONFIRMADO: no se puede iniciar el tránsito");

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CONFIRMADO);
        assertThat(viaje.getEventos()).hasSize(1);
    }

    /** Ningún valor del enum queda sin su objeto de estado, y cada uno está anotado bajo su propio tipo. */
    @ParameterizedTest
    @EnumSource(TipoEstadoViaje.class)
    void cadaTipoTieneSuEstado(TipoEstadoViaje tipo) {
        assertThat(EstadosViaje.de(tipo)).isNotNull();
        assertThat(EstadosViaje.de(tipo).tipo()).isEqualTo(tipo);
    }

    /** Los estados que completan FLY-28, FLY-30 y FLY-31 todavía no permiten ninguna acción. */
    @ParameterizedTest
    @EnumSource(value = TipoEstadoViaje.class,
            names = {"CONFORMADO", "EN_DISPUTA", "CERRADO_CON_DISPUTA", "CANCELADO"})
    void losEstadosQueFaltanNoPermitenNada(TipoEstadoViaje tipo) {
        EstadoViaje estado = EstadosViaje.de(tipo);
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        assertThat(estado).isInstanceOf(EstadoSinAcciones.class);
        assertThatThrownBy(() -> estado.cargar(viaje, TRANSPORTISTA)).isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> estado.cancelar(viaje, "motivo", TRANSPORTISTA))
                .isInstanceOf(TransicionNoPermitidaException.class);
    }
}
