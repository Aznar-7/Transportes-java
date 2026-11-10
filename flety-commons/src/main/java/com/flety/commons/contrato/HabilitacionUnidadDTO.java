package com.flety.commons.contrato;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de GET /internal/unidades/{id}/habilitacion?hasta= (transporte-service a cargas-service).
 * Dice si la unidad puede proponer hasta esa fecha (RN-04) y trae los datos que cargas necesita para validar
 * la propuesta (carrocería, capacidad, RN-06) y para copiar en el viaje al confirmar.
 *
 * @param habilitada            true si el transportista está VERIFICADO y los documentos obligatorios están aprobados y vigentes
 * @param motivos               por qué no está habilitada (vacío si lo está), en texto para mostrar
 * @param sumaAseguradaCarga    suma asegurada de la póliza de carga; null si no tiene una aprobada
 * @param transportistaSub      sub del JWT del transportista dueño: cargas-service lo compara con el token (RN-T1)
 */
public record HabilitacionUnidadDTO(
        Long unidadId,
        boolean habilitada,
        List<String> motivos,
        String dominio,
        TipoCarroceria tipoCarroceria,
        BigDecimal capacidadToneladas,
        BigDecimal capacidadM3,
        BigDecimal sumaAseguradaCarga,
        Long transportistaId,
        String transportistaRazonSocial,
        String transportistaTelefono,
        String transportistaSub
) {
    public HabilitacionUnidadDTO {
        motivos = motivos == null ? List.of() : List.copyOf(motivos);
    }
}
