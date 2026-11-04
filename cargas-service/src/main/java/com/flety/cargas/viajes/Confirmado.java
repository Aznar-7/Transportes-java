package com.flety.cargas.viajes;

/**
 * El dador aceptó la propuesta (FLY-26). Lo que sigue es que el transportista cargue o que una de las partes
 * cancele (FLY-28). Es el único estado que permite cancelar: una vez cargada la mercadería, ya no (409).
 */
final class Confirmado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.CONFIRMADO;
    }

    @Override
    public void cargar(Viaje viaje, String actorSub) {
        viaje.cambiarEstado(TipoEstadoViaje.CARGADO, actorSub, null);
    }

    @Override
    public void cancelar(Viaje viaje, String motivo, String actorSub) {
        viaje.marcarCancelado(motivo, actorSub);
    }
}
