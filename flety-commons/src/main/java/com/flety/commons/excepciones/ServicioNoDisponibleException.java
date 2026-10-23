package com.flety.commons.excepciones;

/**
 * Otro microservicio de Flety no respondió (por ejemplo cargas-service no llega a transporte-service).
 * Se falla cerrado: no se crea ni se confirma nada y se responde 503.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
