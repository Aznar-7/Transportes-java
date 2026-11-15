package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoCarga;
import com.flety.commons.excepciones.ConflictoException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La conformidad en memoria y sin Spring: una sola transición, que dispara el dador dentro de la ventana o el sistema
 * cuando la ventana venció. Lo único que cambia entre los dos es quién queda registrado.
 */
class ConformidadDelViajeTest {

    private static final String DADOR = ViajesDePrueba.SUB_DADOR;
    private static final String SISTEMA = EventoViaje.ACTOR_SISTEMA;

    @Test
    void elDadorConformaDentroDeLaVentana() {
        Viaje viaje = entregado();

        viaje.conformar(ConformadoPor.DADOR, DADOR);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CONFORMADO);
        assertThat(viaje.getConformadoPor()).isEqualTo(ConformadoPor.DADOR);
        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CERRADA);
        EventoViaje evento = viaje.getEventos().getLast();
        assertThat(evento.getEstadoAnterior()).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(evento.getActorSub()).isEqualTo(DADOR);
        assertThat(evento.getDetalle()).isEqualTo("Conformó el dador");
    }

    @Test
    void elSistemaConformaCuandoLaVentanaVencio() {
        Viaje viaje = entregadoConVentanaVencida();

        viaje.conformar(ConformadoPor.SISTEMA, SISTEMA);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CONFORMADO);
        assertThat(viaje.getConformadoPor()).isEqualTo(ConformadoPor.SISTEMA);
        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CERRADA);
        assertThat(viaje.getEventos().getLast().getActorSub()).isEqualTo(SISTEMA);
        assertThat(viaje.getEventos().getLast().getDetalle())
                .isEqualTo("Conformidad automática: pasaron 48 h sin conformidad ni disputa");
    }

    @Test
    void elDadorFueraDeLaVentanaRecibeConflictoYNadaCambia() {
        Viaje viaje = entregadoConVentanaVencida();
        int eventos = viaje.getEventos().size();

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.DADOR, DADOR))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("venció")
                .hasMessageContaining("la conformidad ya la da el sistema");

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
        assertThat(viaje.getConformadoPor()).isNull();
        assertThat(viaje.getEventos()).hasSize(eventos);
    }

    @Test
    void elSistemaNoSeAdelantaMientrasLaVentanaEstaAbierta() {
        Viaje viaje = entregado();

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.SISTEMA, SISTEMA))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("sigue abierta");

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
    }

    @Test
    void antesDeLaEntregaNoSeConforma() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.DADOR, DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está CONFIRMADO: no se puede dar conformidad");
    }

    @Test
    void conformadoEsFinal() {
        Viaje viaje = entregado();
        viaje.conformar(ConformadoPor.DADOR, DADOR);

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.DADOR, DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está CONFORMADO: no se puede dar conformidad");
        assertThatThrownBy(() -> viaje.disputar("Faltante", "Faltan 2 pallets", DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> viaje.cancelar("motivo", DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThat(EstadosViaje.de(TipoEstadoViaje.CONFORMADO)).isInstanceOf(Conformado.class);
    }

    /** Un viaje en memoria llevado hasta ENTREGADO: la ventana corre desde ahora y vence en 48 h. */
    static Viaje entregado() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();
        viaje.cargar(ViajesDePrueba.SUB_TRANSPORTISTA);
        viaje.iniciarTransito(ViajesDePrueba.SUB_TRANSPORTISTA);
        viaje.registrarEntrega(new DatosEntrega("R-1", "Ana Gómez", "30111222"), ViajesDePrueba.SUB_TRANSPORTISTA);
        return viaje;
    }

    /** Como entregado(), pero con la ventana vencida hace un minuto: el campo no tiene setter, se fija por reflexión. */
    static Viaje entregadoConVentanaVencida() {
        Viaje viaje = entregado();
        ReflectionTestUtils.setField(viaje, "ventanaConformidadHasta", Instant.now().minus(Duration.ofMinutes(1)));
        return viaje;
    }
}
