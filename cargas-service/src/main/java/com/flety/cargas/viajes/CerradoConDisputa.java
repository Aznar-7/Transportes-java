package com.flety.cargas.viajes;

/**
 * Un operador resolvió la disputa en contra del transportista (FLY-31). Es un estado final: no sobrescribe ninguna
 * acción, así que todo da 409.
 */
final class CerradoConDisputa implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.CERRADO_CON_DISPUTA;
    }
}
