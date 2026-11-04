package com.flety.cargas.viajes.dto;

/**
 * Cuerpo opcional de POST /api/cargas/propuestas/{id}/aceptacion.
 *
 * @param aceptoRiesgo RN-06: si el valor declarado de la carga supera la suma asegurada de la unidad, la propuesta
 *                     pide que el dador acepte ese riesgo de forma explícita. Sin cuerpo cuenta como false.
 */
public record AceptacionPropuestaDTO(boolean aceptoRiesgo) {
}
