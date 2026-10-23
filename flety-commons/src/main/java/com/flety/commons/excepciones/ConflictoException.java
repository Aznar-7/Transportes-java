package com.flety.commons.excepciones;

/**
 * El pedido choca con el estado actual del recurso: una propuesta repetida, un documento ya revisado,
 * saltear un hito del viaje. Se responde 409.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
