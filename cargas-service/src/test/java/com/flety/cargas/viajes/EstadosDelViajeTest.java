package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada clase de estado por separado, sin Spring ni base: el viaje se arma en memoria con las mismas carga y
 * propuesta del fixture. Se prueba qué acción permite cada estado, a cuál pasa y qué rechaza.
 */
class EstadosDelViajeTest {

    private static final String ACTOR = ViajesDePrueba.SUB_TRANSPORTISTA;
    private static final DatosEntrega ENTREGA = new DatosEntrega("R-0001-00012345", "Ana Gómez", "30111222");

    @Test
    void confirmadoSoloPermiteCargar() {
        Viaje viaje = viajeEnMemoria();
        Confirmado confirmado = new Confirmado();

        assertThatThrownBy(() -> confirmado.iniciarTransito(viaje, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> confirmado.registrarEntrega(viaje, ENTREGA, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);

        confirmado.cargar(viaje, ACTOR);
        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CARGADO);
    }

    @Test
    void cargadoSoloPermiteIniciarElTransito() {
        Viaje viaje = viajeEnMemoria();
        Cargado cargado = new Cargado();

        assertThatThrownBy(() -> cargado.cargar(viaje, ACTOR)).isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> cargado.registrarEntrega(viaje, ENTREGA, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);

        cargado.iniciarTransito(viaje, ACTOR);
        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.EN_TRANSITO);
    }

    @Test
    void enTransitoSoloPermiteEntregarYConComprobante() {
        Viaje viaje = viajeEnMemoria();
        EnTransito enTransito = new EnTransito();

        assertThatThrownBy(() -> enTransito.cargar(viaje, ACTOR)).isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> enTransito.iniciarTransito(viaje, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> enTransito.registrarEntrega(viaje, null, ACTOR))
                .isInstanceOf(NullPointerException.class);

        enTransito.registrarEntrega(viaje, ENTREGA, ACTOR);
        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(viaje.getEventos().getLast().getDetalle()).isEqualTo("Remito R-0001-00012345, recibió Ana Gómez");
    }

    @Test
    void entregadoNoPermiteMasHitosDelTransportista() {
        Viaje viaje = viajeEnMemoria();
        Entregado entregado = new Entregado();

        assertThatThrownBy(() -> entregado.cargar(viaje, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está ENTREGADO: no se puede cargar");
        assertThatThrownBy(() -> entregado.iniciarTransito(viaje, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> entregado.registrarEntrega(viaje, ENTREGA, ACTOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
    }

    @Test
    void cadaTransicionDejaUnEventoConElActor() {
        Viaje viaje = viajeEnMemoria();

        new Confirmado().cargar(viaje, ACTOR);

        EventoViaje evento = viaje.getEventos().getLast();
        assertThat(evento.getEstadoAnterior()).isEqualTo(TipoEstadoViaje.CONFIRMADO);
        assertThat(evento.getEstadoNuevo()).isEqualTo(TipoEstadoViaje.CARGADO);
        assertThat(evento.getActorSub()).isEqualTo(ACTOR);
    }

    /** Un viaje recién confirmado, sin guardar: los estados solo tocan el objeto. */
    static Viaje viajeEnMemoria() {
        Carga carga = ViajesDePrueba.nuevaCarga(Instant.now().plus(1, ChronoUnit.DAYS));
        return new Viaje(carga, ViajesDePrueba.nuevaPropuesta(carga, 10L), "AB123CD",
                new Contacto("Transportes Pérez SRL", "+54 351 555-1234"), null, ViajesDePrueba.SUB_DADOR);
    }
}
