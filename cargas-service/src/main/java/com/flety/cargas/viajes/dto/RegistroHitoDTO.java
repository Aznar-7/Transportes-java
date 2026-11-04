package com.flety.cargas.viajes.dto;

import com.flety.cargas.viajes.DatosEntrega;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/viajes/{id}/hitos: el hito que registra el transportista.
 *
 * @param hito        el estado al que quiere pasar el viaje
 * @param comprobante solo con ENTREGADO
 */
public record RegistroHitoDTO(
        @NotNull(message = "falta el hito: CARGADO, EN_TRANSITO o ENTREGADO")
        Hito hito,
        ComprobanteDTO comprobante
) {

    /**
     * Los hitos que registra el transportista, en el orden en que se dan. Conformar, disputar y cancelar no van
     * acá: son de otros actores y tienen su propio endpoint (FLY-28, FLY-30 y FLY-31).
     */
    public enum Hito {
        CARGADO,
        EN_TRANSITO,
        ENTREGADO
    }

    public record ComprobanteDTO(String numeroRemito, String recibioNombre, String recibioDni) {

        public DatosEntrega aDatos() {
            return new DatosEntrega(numeroRemito, recibioNombre, recibioDni);
        }
    }
}
