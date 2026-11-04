package com.flety.cargas.viajes;

/** La mercadería está arriba del camión. Lo que sigue es salir hacia el destino. */
final class Cargado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.CARGADO;
    }

    @Override
    public void iniciarTransito(Viaje viaje, String actorSub) {
        viaje.cambiarEstado(TipoEstadoViaje.EN_TRANSITO, actorSub, null);
    }
}
