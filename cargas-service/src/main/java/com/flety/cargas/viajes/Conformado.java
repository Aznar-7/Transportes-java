package com.flety.cargas.viajes;

/**
 * El viaje terminó con conformidad: la dio el dador o, pasadas las 48 h sin disputa, el sistema (FLY-30).
 * Es un estado final: no sobrescribe ninguna acción, así que conformar de nuevo, disputar o cancelar dan 409.
 */
final class Conformado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.CONFORMADO;
    }
}
