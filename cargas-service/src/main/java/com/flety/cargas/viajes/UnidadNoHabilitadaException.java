package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ReglaNegocioException;

import java.util.List;

/**
 * Al aceptar, la unidad de la propuesta ya no estaba habilitada (RN-04, flujo C): por ejemplo, se le venció el
 * seguro desde que se propuso. Hereda de ReglaNegocioException, así el manejador común responde 422.
 * Para cuando se lanza, la propuesta ya quedó INVALIDADA en su propia transacción.
 */
public class UnidadNoHabilitadaException extends ReglaNegocioException {

    public UnidadNoHabilitadaException(List<String> motivos) {
        super("La unidad de esta propuesta ya no está habilitada"
                + (motivos.isEmpty() ? "" : ": " + String.join(", ", motivos))
                + ". La propuesta quedó INVALIDADA; podés elegir otra.");
    }
}
