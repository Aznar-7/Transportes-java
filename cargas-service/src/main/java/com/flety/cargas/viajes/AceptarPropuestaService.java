package com.flety.cargas.viajes;

import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

/**
 * Flujo C: el dador acepta una propuesta y nace el viaje.
 * <p>
 * Este service no es @Transactional a propósito: orquesta pasos y deja cada transacción en ConfirmacionDeViaje.
 * Así una llamada lenta a otro servicio no tiene una transacción abierta (ni filas bloqueadas) mientras espera.
 */
@Service
public class AceptarPropuestaService {

    private final ConfirmacionDeViaje confirmacion;
    private final UsuarioActual usuarioActual;

    public AceptarPropuestaService(ConfirmacionDeViaje confirmacion, UsuarioActual usuarioActual) {
        this.confirmacion = confirmacion;
        this.usuarioActual = usuarioActual;
    }

    /** @return el id del viaje creado */
    public Long aceptar(Long propuestaId, boolean aceptoRiesgo) {
        String dadorSub = usuarioActual.id();
        confirmacion.preparar(propuestaId, dadorSub, aceptoRiesgo);
        return confirmacion.confirmar(propuestaId, dadorSub);
    }
}
