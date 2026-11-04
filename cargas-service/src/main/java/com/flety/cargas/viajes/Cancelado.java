package com.flety.cargas.viajes;

/**
 * Una de las partes canceló el viaje antes de cargar (FLY-28). Es un estado final: no permite ninguna acción.
 * Por eso no sobrescribe nada: cancelar de nuevo, cargar o cualquier otra acción dan 409.
 */
final class Cancelado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.CANCELADO;
    }
}
