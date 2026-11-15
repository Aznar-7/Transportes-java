package com.flety.cargas.viajes;

/**
 * El dador reclamó dentro de la ventana (FLY-31) y el viaje espera a que un operador resuelva. Lo único que permite
 * es resolver la disputa.
 * <p>
 * Por qué el Reloj de conformidad no lo toca: no sobrescribe conformar, así que cualquier intento de conformar
 * (del dador o del sistema) da 409. El Reloj no sabe nada de disputas: la regla está en el estado, y el Reloj
 * además solo busca viajes ENTREGADOS. Son dos frenos que no acoplan la tarea programada con las disputas.
 */
final class EnDisputa implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.EN_DISPUTA;
    }

    /**
     * A favor del transportista, el viaje queda CONFORMADO por el OPERADOR, con la misma marcarConformado del dador y
     * del Reloj. En contra, queda CERRADO_CON_DISPUTA. En los dos casos el viaje terminó y la carga queda CERRADA.
     */
    @Override
    public void resolverDisputa(Viaje viaje, boolean aFavorDelTransportista, String resolucion, String operadorSub) {
        if (aFavorDelTransportista) {
            viaje.marcarConformado(ConformadoPor.OPERADOR, operadorSub);
        } else {
            viaje.marcarCerradoConDisputa(resolucion, operadorSub);
        }
    }
}
