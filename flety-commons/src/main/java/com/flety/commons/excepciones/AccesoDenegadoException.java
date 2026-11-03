package com.flety.commons.excepciones;

/**
 * El usuario tiene el rol correcto, pero el recurso no es suyo (RN-T1): un viaje del que no es parte,
 * la unidad de otro transportista. Se responde 403 con este mensaje como detalle.
 * <p>
 * La diferencia con un AccessDeniedException de Spring es que esto lo decide el service mirando al dueño,
 * no @PreAuthorize mirando el rol, y por eso el mensaje dice el motivo real.
 * El mensaje lo ve el cliente: no tiene que llevar ids de otros usuarios ni datos del recurso.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
