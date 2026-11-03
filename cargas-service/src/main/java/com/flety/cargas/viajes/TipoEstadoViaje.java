package com.flety.cargas.viajes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los estados por los que pasa un viaje (sección 6 del diseño), tal como se guardan en la base.
 * <p>
 * Se llama TipoEstadoViaje y no EstadoViaje porque ese nombre es para la interfaz del patrón State (FLY-27):
 * cada valor de este enum va a tener su clase de estado, que sabe qué acciones permite y a cuál pasa.
 */
public enum TipoEstadoViaje {
    /** El dador aceptó una propuesta (FLY-26). Desde acá las partes ven los contactos. */
    CONFIRMADO,
    /** El transportista cargó la mercadería. */
    CARGADO,
    EN_TRANSITO,
    /** Llegó con comprobante de entrega: corre la ventana de conformidad de 48 h. */
    ENTREGADO,
    /** El dador dio conformidad, o venció la ventana y la dio el sistema. */
    CONFORMADO,
    /** El dador abrió una disputa dentro de la ventana de conformidad. */
    EN_DISPUTA,
    /** Un operador resolvió la disputa. */
    CERRADO_CON_DISPUTA,
    CANCELADO;

    /** Terminado para siempre: ya no admite acciones. */
    public boolean cerrado() {
        return this == CONFORMADO || this == CERRADO_CON_DISPUTA || this == CANCELADO;
    }

    /**
     * Cuenta para RN-15 (una unidad no puede tener dos viajes con ventanas superpuestas): todo lo que no terminó.
     * Un viaje EN_DISPUTA sigue activo porque todavía no está cerrado.
     */
    public boolean activo() {
        return !cerrado();
    }

    /** Los estados activos, para filtrar en la base. */
    public static Set<TipoEstadoViaje> activos() {
        return Arrays.stream(values()).filter(TipoEstadoViaje::activo)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoEstadoViaje.class)));
    }
}
