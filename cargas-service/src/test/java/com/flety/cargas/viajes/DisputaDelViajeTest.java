package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoCarga;
import com.flety.commons.excepciones.ConflictoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las disputas en memoria y sin Spring: abrirla dentro de la ventana frena el viaje en EN_DISPUTA, y el operador lo
 * cierra como CONFORMADO (a favor del transportista) o CERRADO_CON_DISPUTA (en contra).
 */
class DisputaDelViajeTest {

    private static final String DADOR = ViajesDePrueba.SUB_DADOR;
    private static final String OPERADOR = "sub-operador1";

    @Test
    void elDadorDisputaDentroDeLaVentana() {
        Viaje viaje = ConformidadDelViajeTest.entregado();

        Disputa disputa = viaje.disputar("Faltante", "Llegaron 18 pallets de 20", DADOR);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.EN_DISPUTA);
        assertThat(disputa.getEstado()).isEqualTo(EstadoDisputa.ABIERTA);
        assertThat(disputa.getMotivo()).isEqualTo("Faltante");
        assertThat(disputa.getAbiertaPorSub()).isEqualTo(DADOR);
        assertThat(disputa.getViaje()).isSameAs(viaje);
        assertThat(viaje.getEventos().getLast().getDetalle()).isEqualTo("Disputa abierta: Faltante");
        // Mientras dura la disputa la carga sigue ASIGNADA: el viaje todavía no terminó.
        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.ASIGNADA);
    }

    @Test
    void fueraDeLaVentanaNoSeDisputa() {
        Viaje viaje = ConformidadDelViajeTest.entregadoConVentanaVencida();

        assertThatThrownBy(() -> viaje.disputar("Faltante", "x", DADOR))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("la conformidad ya la da el sistema");
        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
    }

    @Test
    void antesDeLaEntregaNoSeDisputa() {
        Viaje viaje = EstadosDelViajeTest.viajeEnMemoria();

        assertThatThrownBy(() -> viaje.disputar("Faltante", "x", DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está CONFIRMADO: no se puede abrir una disputa");
    }

    /** La regla que frena al Reloj: en disputa, ni el sistema ni el dador pueden conformar. */
    @Test
    void enDisputaNadieConforma() {
        Viaje viaje = enDisputa();

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.SISTEMA, EventoViaje.ACTOR_SISTEMA))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está EN_DISPUTA: no se puede dar conformidad");
        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.DADOR, DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
        assertThatThrownBy(() -> viaje.disputar("Otra", "x", DADOR))
                .isInstanceOf(TransicionNoPermitidaException.class);
    }

    @Test
    void aFavorDelTransportistaQuedaConformadoPorElOperador() {
        Viaje viaje = enDisputa();

        viaje.resolverDisputa(true, "El remito muestra los 20 pallets firmados", OPERADOR);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CONFORMADO);
        assertThat(viaje.getConformadoPor()).isEqualTo(ConformadoPor.OPERADOR);
        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CERRADA);
        EventoViaje evento = viaje.getEventos().getLast();
        assertThat(evento.getEstadoAnterior()).isEqualTo(TipoEstadoViaje.EN_DISPUTA);
        assertThat(evento.getActorSub()).isEqualTo(OPERADOR);
        assertThat(evento.getDetalle()).isEqualTo("Disputa resuelta a favor del transportista");
    }

    @Test
    void enContraDelTransportistaQuedaCerradoConDisputa() {
        Viaje viaje = enDisputa();

        viaje.resolverDisputa(false, "Faltaban 2 pallets", OPERADOR);

        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CERRADO_CON_DISPUTA);
        assertThat(viaje.getConformadoPor()).isNull();
        assertThat(viaje.getCarga().getEstado()).isEqualTo(EstadoCarga.CERRADA);
        assertThat(viaje.getEventos().getLast().getDetalle())
                .isEqualTo("Disputa resuelta en contra del transportista: Faltaban 2 pallets");
        assertThat(EstadosViaje.de(TipoEstadoViaje.CERRADO_CON_DISPUTA)).isInstanceOf(CerradoConDisputa.class);
    }

    @Test
    void sinDisputaAbiertaNoHayNadaQueResolver() {
        Viaje viaje = ConformidadDelViajeTest.entregado();

        assertThatThrownBy(() -> viaje.resolverDisputa(true, "x", OPERADOR))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessage("El viaje está ENTREGADO: no se puede resolver una disputa");
    }

    @Test
    void unaDisputaSeResuelveUnaSolaVez() {
        Viaje viaje = ConformidadDelViajeTest.entregado();
        Disputa disputa = viaje.disputar("Faltante", "x", DADOR);

        disputa.resolver(false, "Faltaban 2 pallets", OPERADOR);

        assertThat(disputa.getEstado()).isEqualTo(EstadoDisputa.RESUELTA);
        assertThat(disputa.getAFavorDelTransportista()).isFalse();
        assertThat(disputa.getOperadorSub()).isEqualTo(OPERADOR);
        assertThat(disputa.getResueltaEn()).isNotNull();
        assertThatThrownBy(() -> disputa.resolver(true, "otra vez", OPERADOR))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("La disputa ya está resuelta");
    }

    /** El operador conforma solo resolviendo una disputa, nunca directo sobre un viaje ENTREGADO. */
    @Test
    void elOperadorNoConformaDirectoUnViajeEntregado() {
        Viaje viaje = ConformidadDelViajeTest.entregado();

        assertThatThrownBy(() -> viaje.conformar(ConformadoPor.OPERADOR, OPERADOR))
                .isInstanceOf(ConflictoException.class);
        assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.ENTREGADO);
    }

    private static Viaje enDisputa() {
        Viaje viaje = ConformidadDelViajeTest.entregado();
        viaje.disputar("Faltante", "Llegaron 18 pallets de 20", DADOR);
        return viaje;
    }
}
