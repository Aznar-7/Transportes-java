package com.flety.cargas.mercado.dto;

/**
 * Lo que devuelve GET /api/cargas/{id}: el mismo recurso con datos distintos según quién mira (RN-13).
 * Son dos tipos y no uno con campos en null para que un dato reservado no se pueda filtrar por olvidarse de borrarlo.
 */
public sealed interface CargaVista permits CargaResponse, CargaReservadaResponse {
}
