package com.flety.commons.excepciones;

/**
 * Lo que se pidió no existe (una carga, un retorno, una unidad). Se responde 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " " + id + " no existe");
    }
}
