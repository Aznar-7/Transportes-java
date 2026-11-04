package com.flety.cargas.viajes.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flety.cargas.viajes.DatosEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/viajes/{id}/hitos: el hito que registra el transportista.
 *
 * @param hito        el estado al que quiere pasar el viaje
 * @param comprobante obligatorio con ENTREGADO, y solo con ENTREGADO
 */
public record RegistroHitoDTO(
        @NotNull(message = "falta el hito: CARGADO, EN_TRANSITO o ENTREGADO")
        Hito hito,
        @Valid
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

    /**
     * Entregar sin comprobante es un pedido incompleto, no un problema de estado: por eso es 400, y se responde
     * antes de mirar el viaje. Mandarlo con otro hito también es 400: un comprobante que se ignora en silencio
     * haría creer al transportista que la entrega quedó registrada.
     * <p>
     * El nombre del método es el campo que aparece en "errores" de la respuesta. @JsonIgnore evita que Jackson lo
     * tome como una propiedad más del JSON.
     */
    @JsonIgnore
    @AssertTrue(message = "el comprobante es obligatorio con ENTREGADO y no va con los demás hitos")
    public boolean isComprobanteSegunElHito() {
        return hito == null || (hito == Hito.ENTREGADO) == (comprobante != null);
    }

    /**
     * El remito y quién recibió: es lo que se mira si después hay una disputa, así que va completo.
     */
    public record ComprobanteDTO(
            @NotBlank(message = "falta el número de remito")
            @Size(max = 50, message = "el número de remito tiene hasta 50 caracteres")
            String numeroRemito,
            @NotBlank(message = "falta quién recibió la carga")
            @Size(max = 120, message = "el nombre tiene hasta 120 caracteres")
            String recibioNombre,
            @NotBlank(message = "falta el DNI de quien recibió")
            @Pattern(regexp = "\\d{7,8}", message = "el DNI son 7 u 8 dígitos, sin puntos")
            String recibioDni
    ) {

        public DatosEntrega aDatos() {
            return new DatosEntrega(numeroRemito, recibioNombre, recibioDni);
        }
    }
}
