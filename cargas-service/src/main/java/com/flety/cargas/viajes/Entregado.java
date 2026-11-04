package com.flety.cargas.viajes;

/**
 * La carga llegó con su comprobante y corre la ventana de conformidad de 48 h. Ya no hay más hitos del
 * transportista: lo que sigue es del dador. Dar conformidad o abrir una disputa lo agregan FLY-30 y FLY-31;
 * hasta entonces, este estado no permite ninguna acción.
 */
final class Entregado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.ENTREGADO;
    }
}
