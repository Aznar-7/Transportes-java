package com.flety.cargas.viajes;

import java.util.Objects;

/** El camión va en camino. Lo que sigue es entregar, y para eso hace falta el comprobante. */
final class EnTransito implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.EN_TRANSITO;
    }

    /**
     * La API ya responde 400 si falta el comprobante; este control es para cualquier otro camino que llegue
     * al dominio sin pasar por la API: sin comprobante no hay entrega.
     */
    @Override
    public void registrarEntrega(Viaje viaje, DatosEntrega entrega, String actorSub) {
        Objects.requireNonNull(entrega, "Sin comprobante no se puede registrar la entrega");
        viaje.cambiarEstado(TipoEstadoViaje.ENTREGADO, actorSub, "Remito " + entrega.numeroRemito()
                + ", recibió " + entrega.recibioNombre());
    }
}
