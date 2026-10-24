package com.flety.cargas.mercado;

/**
 * Ciclo de vida de una propuesta (sección 6 del diseño).
 */
public enum EstadoPropuesta {
    /** Esperando que el dador decida. */
    PENDIENTE,
    /** El dador la eligió: se creó el viaje. */
    ACEPTADA,
    /** El dador la rechazó, o aceptó otra. */
    RECHAZADA,
    /** El transportista la retiró. */
    RETIRADA,
    /** Pasaron 24 h o cerró la ventana de carga (RN-16). */
    VENCIDA,
    /** Al aceptarla, la unidad ya no estaba habilitada (flujo C). */
    INVALIDADA
}
