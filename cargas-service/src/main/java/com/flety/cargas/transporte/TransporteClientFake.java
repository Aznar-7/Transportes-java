package com.flety.cargas.transporte;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * TransporteClient falso para tests y para desarrollar sin transporte-service.
 * Se carga con los retornos y habilitaciones que el test necesite; lo que no se cargó responde 404,
 * y con simularCaida(true) se comporta como transporte caído (503).
 *
 * <pre>
 * var transporte = new TransporteClientFake()
 *         .conRetorno(retorno)
 *         .conHabilitacion(habilitacion);
 * </pre>
 */
public class TransporteClientFake implements TransporteClient {

    private final Map<Long, RetornoInternoDTO> retornos = new HashMap<>();
    private final Map<Long, HabilitacionUnidadDTO> habilitaciones = new HashMap<>();
    private boolean caido;

    public TransporteClientFake conRetorno(RetornoInternoDTO retorno) {
        retornos.put(retorno.id(), retorno);
        return this;
    }

    public TransporteClientFake conHabilitacion(HabilitacionUnidadDTO habilitacion) {
        habilitaciones.put(habilitacion.unidadId(), habilitacion);
        return this;
    }

    public TransporteClientFake simularCaida(boolean caido) {
        this.caido = caido;
        return this;
    }

    @Override
    public RetornoInternoDTO obtenerRetorno(Long retornoId) {
        verificarDisponible();
        RetornoInternoDTO retorno = retornos.get(retornoId);
        if (retorno == null) {
            throw new RecursoNoEncontradoException("Retorno", retornoId);
        }
        return retorno;
    }

    /** El fake no mira la fecha "hasta": devuelve la habilitación tal como se cargó. */
    @Override
    public HabilitacionUnidadDTO consultarHabilitacion(Long unidadId, Instant hasta) {
        verificarDisponible();
        HabilitacionUnidadDTO habilitacion = habilitaciones.get(unidadId);
        if (habilitacion == null) {
            throw new RecursoNoEncontradoException("Unidad", unidadId);
        }
        return habilitacion;
    }

    private void verificarDisponible() {
        if (caido) {
            throw new ServicioNoDisponibleException("transporte-service no responde (fake)");
        }
    }
}
