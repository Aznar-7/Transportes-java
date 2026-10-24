package com.flety.cargas.transporte;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;

import java.time.Instant;

/**
 * Lo que cargas-service le pregunta a transporte-service (sección 5 del diseño).
 * La implementación real con RestClient llega en FLY-21; hasta entonces se programa y se prueba con TransporteClientFake.
 * Falla cerrada: si transporte no responde se lanza ServicioNoDisponibleException (503) y no se crea ni se confirma nada.
 */
public interface TransporteClient {

    /**
     * GET /internal/retornos/{id}. Flujo A: buscar cargas de paso.
     *
     * @throws RecursoNoEncontradoException si el retorno no existe (404)
     * @throws ServicioNoDisponibleException si transporte no responde (503)
     */
    RetornoInternoDTO obtenerRetorno(Long retornoId);

    /**
     * GET /internal/unidades/{id}/habilitacion?hasta=. Flujos B y C: proponer y aceptar (RN-04).
     *
     * @param hasta fin de la ventana de descarga de la carga: los documentos tienen que estar vigentes hasta ahí
     * @throws RecursoNoEncontradoException si la unidad no existe (404)
     * @throws ServicioNoDisponibleException si transporte no responde (503)
     */
    HabilitacionUnidadDTO consultarHabilitacion(Long unidadId, Instant hasta);
}
