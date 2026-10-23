package com.flety.commons.excepciones;

/**
 * Se rompió una regla de negocio que depende del estado del sistema (por ejemplo RN-07: el dador no declaró B2B).
 * El pedido estaba bien armado, pero no se puede cumplir. Se responde 422.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
