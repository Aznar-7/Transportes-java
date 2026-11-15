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
        boolean abierta = ventanaAbierta(viaje);
        if (por == ConformadoPor.DADOR && !abierta) {
            throw ventanaVencida(viaje);
        }
        if (por == ConformadoPor.SISTEMA && abierta) {
            throw new ConflictoException("La ventana de conformidad sigue abierta hasta "
                    + viaje.getVentanaConformidadHasta() + ": todavía puede conformar o disputar el dador");
        }
        if (por == ConformadoPor.OPERADOR) {
            // El operador solo conforma al resolver una disputa (EnDisputa), nunca directo desde ENTREGADO.
            throw new ConflictoException("Un operador conforma solo al resolver una disputa");
        }
        viaje.marcarConformado(por, actorSub);
    }

    /**
     * El dador reclama dentro de la ventana, con la misma regla de tiempo que la conformidad: pasadas las 48 h ya no
     * puede ni conformar ni disputar, porque la conformidad la da el sistema.
     */
    @Override
    public void disputar(Viaje viaje, String motivo, String descripcion, String actorSub) {
        if (!ventanaAbierta(viaje)) {
            throw ventanaVencida(viaje);
        }
        viaje.marcarEnDisputa(motivo, actorSub);
    }

    /** La ventana incluye el instante exacto en que vence. */
    private static boolean ventanaAbierta(Viaje viaje) {
        return !Instant.now().isAfter(viaje.getVentanaConformidadHasta());
    }

    private static ConflictoException ventanaVencida(Viaje viaje) {
        return new ConflictoException("La ventana de conformidad venció el " + viaje.getVentanaConformidadHasta()
                + ": la conformidad ya la da el sistema");
    }
}
