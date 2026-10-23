package com.flety.commons.excepciones;

/**
 * Una API de afuera (OpenRouteService o Georef) se cayó, tardó demasiado o respondió algo inesperado.
 * Quien la usa decide si degrada (ruta aproximada) o corta; si llega al usuario, se responde 503.
 */
public class ServicioExternoException extends RuntimeException {

    public ServicioExternoException(String mensaje) {
        super(mensaje);
    }

    public ServicioExternoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
