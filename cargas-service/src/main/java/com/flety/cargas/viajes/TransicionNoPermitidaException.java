package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ReglaNegocioException;
import lombok.Getter;

/**
 * El estado actual del viaje no permite la acción pedida: saltear un hito, repetirlo o volver atrás.
 * <p>
 * Es una ReglaNegocioException, como pide la historia: el ciclo de vida del viaje es una regla de negocio.
 * Pero la API responde 409 y no el 422 de las demás reglas: el pedido está bien, lo que choca es el estado
 * en que está el viaje ahora (el mismo pedido, en otro momento, se acepta). De eso se encarga
 * ViajesExceptionHandler, que atiende esta excepción antes que el manejador común de flety-commons.
 */
@Getter
public class TransicionNoPermitidaException extends ReglaNegocioException {

    private final TipoEstadoViaje estado;

    public TransicionNoPermitidaException(TipoEstadoViaje estado, String accion) {
        super("El viaje está " + estado + ": no se puede " + accion);
        this.estado = estado;
    }
}
