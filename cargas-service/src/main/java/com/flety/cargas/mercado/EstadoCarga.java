package com.flety.cargas.mercado;

/**
 * Ciclo de vida de una carga (sección 6 del diseño).
 */
public enum EstadoCarga {
    /** Recién publicada, sin propuestas. */
    PUBLICADA,
    /** Llegó al menos una propuesta. */
    CON_PROPUESTAS,
    /** El dador aceptó una propuesta y hay un viaje confirmado. */
    ASIGNADA,
    /** El viaje terminó. */
    CERRADA,
    /** El dador la canceló. */
    CANCELADA,
    /** Terminó la ventana de carga sin viaje (RN-17). */
    VENCIDA
}
