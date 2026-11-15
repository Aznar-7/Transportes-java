package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ConflictoException;

import java.time.Instant;

/**
 * La carga llegó con su comprobante y corre la ventana de conformidad de 48 h. Ya no hay más hitos del
 * transportista: lo que sigue es del dador, que puede conformar (FLY-30) o abrir una disputa (FLY-31).
 * Si en la ventana no hace nada, conforma el sistema.
 */
final class Entregado implements EstadoViaje {

    @Override
    public TipoEstadoViaje tipo() {
        return TipoEstadoViaje.ENTREGADO;
    }

    /**
     * Una sola transición para la persona y para el sistema: lo único que cambia es de qué lado de la ventana tiene
     * que estar cada uno. El dador conforma mientras la ventana está abierta; el Reloj, recién cuando venció. Así no
     * hay dos caminos que puedan dejar el viaje distinto según quién conformó.
     * <p>
     * Los dos rechazos son 409: el pedido está bien, lo que choca es el momento.
     */
    @Override
    public void conformar(Viaje viaje, ConformadoPor por, String actorSub) {
        Instant hasta = viaje.getVentanaConformidadHasta();
        boolean ventanaAbierta = !Instant.now().isAfter(hasta);
        if (por == ConformadoPor.DADOR && !ventanaAbierta) {
            throw new ConflictoException("La ventana de conformidad venció el " + hasta
                    + ": la conformidad ya la da el sistema");
        }
        if (por == ConformadoPor.SISTEMA && ventanaAbierta) {
            throw new ConflictoException("La ventana de conformidad sigue abierta hasta " + hasta
                    + ": todavía puede conformar o disputar el dador");
        }
        viaje.marcarConformado(por, actorSub);
    }
}
