package com.flety.cargas.viajes;

/**
 * El dador aceptó la propuesta (FLY-26). Lo único que sigue es que el transportista cargue.
 * Cancelar también va a salir de acá, pero eso lo agrega FLY-28.
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
}
