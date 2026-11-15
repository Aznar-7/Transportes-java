package com.flety.cargas.viajes;

/**
 * Lugar provisorio para los estados que todavía no tienen su clase: EN_DISPUTA y CERRADO_CON_DISPUTA (FLY-31).
 * No sobrescribe nada, así que prohíbe las seis acciones (409).
 * Esa historia reemplaza este lugar por su propia clase en EstadosViaje.
 */
record EstadoSinAcciones(TipoEstadoViaje tipo) implements EstadoViaje {
}
