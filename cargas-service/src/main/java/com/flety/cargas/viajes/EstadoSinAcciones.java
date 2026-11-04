package com.flety.cargas.viajes;

/**
 * Lugar provisorio para los estados que todavía no tienen su clase: CANCELADO (FLY-28), CONFORMADO (FLY-30),
 * EN_DISPUTA y CERRADO_CON_DISPUTA (FLY-31). No sobrescribe nada, así que prohíbe las seis acciones (409).
 * Cada una de esas historias reemplaza este lugar por su propia clase en EstadosViaje.
 */
record EstadoSinAcciones(TipoEstadoViaje tipo) implements EstadoViaje {
}
