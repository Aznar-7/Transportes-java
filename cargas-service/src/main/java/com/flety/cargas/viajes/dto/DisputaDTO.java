package com.flety.cargas.viajes.dto;

import com.flety.cargas.viajes.Disputa;
import com.flety.cargas.viajes.EstadoDisputa;
import com.flety.cargas.viajes.TipoEstadoViaje;

import java.time.Instant;

/**
 * Una disputa, tal como la ven las partes del viaje y el operador (FLY-31).
 *
 * @param estadoViaje            el estado del viaje ahora: EN_DISPUTA mientras esté abierta, y después CONFORMADO o
 *                               CERRADO_CON_DISPUTA según cómo se resolvió
 * @param aFavorDelTransportista null mientras la disputa siga abierta
 */
public record DisputaDTO(
        Long id,
        Long viajeId,
        TipoEstadoViaje estadoViaje,
        String motivo,
        String descripcion,
        String abiertaPorSub,
        Instant abiertaEn,
        EstadoDisputa estado,
        Boolean aFavorDelTransportista,
        String resolucion,
        String operadorSub,
        Instant resueltaEn
) {

    /** Se llama dentro de una transacción: lee el viaje (LAZY) de la disputa. */
    public static DisputaDTO desde(Disputa disputa) {
        return new DisputaDTO(disputa.getId(), disputa.getViaje().getId(), disputa.getViaje().getEstado(),
                disputa.getMotivo(), disputa.getDescripcion(), disputa.getAbiertaPorSub(), disputa.getAbiertaEn(),
                disputa.getEstado(), disputa.getAFavorDelTransportista(), disputa.getResolucion(),
                disputa.getOperadorSub(), disputa.getResueltaEn());
    }
}
