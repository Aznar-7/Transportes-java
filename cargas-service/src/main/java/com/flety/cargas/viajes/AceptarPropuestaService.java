package com.flety.cargas.viajes;

import com.flety.cargas.transporte.TransporteClient;
import com.flety.cargas.viajes.ConfirmacionDeViaje.DatosAceptacion;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

/**
 * Flujo C: el dador acepta una propuesta y nace el viaje.
 * <p>
 * Este service no es @Transactional a propósito: orquesta pasos y deja cada transacción en ConfirmacionDeViaje.
 * Así la llamada HTTP a transporte-service (que puede tardar hasta el timeout) no tiene una transacción abierta
 * ni filas bloqueadas mientras espera.
 */
@Service
public class AceptarPropuestaService {

    private final ConfirmacionDeViaje confirmacion;
    private final TransporteClient transporte;
    private final UsuarioActual usuarioActual;

    public AceptarPropuestaService(ConfirmacionDeViaje confirmacion, TransporteClient transporte,
                                   UsuarioActual usuarioActual) {
        this.confirmacion = confirmacion;
        this.transporte = transporte;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Si transporte-service no responde, TransporteClient lanza ServicioNoDisponibleException (503) y no se
     * confirma nada: se falla cerrado.
     *
     * @return el id del viaje creado
     */
    public Long aceptar(Long propuestaId, boolean aceptoRiesgo) {
        String dadorSub = usuarioActual.id();
        DatosAceptacion datos = confirmacion.preparar(propuestaId, dadorSub, aceptoRiesgo);
        HabilitacionUnidadDTO habilitacion = transporte.consultarHabilitacion(datos.unidadId(), datos.hasta());
        return confirmacion.confirmar(propuestaId, dadorSub, habilitacion);
    }
}
