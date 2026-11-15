package com.flety.cargas.viajes;

/**
 * Quién dio la conformidad de la entrega: el dador a mano, el sistema cuando vencen las 48 h sin disputa (FLY-30),
 * o un operador al resolver una disputa a favor del transportista (FLY-31).
 */
public enum ConformadoPor {
    DADOR,
    SISTEMA,
    OPERADOR
}
