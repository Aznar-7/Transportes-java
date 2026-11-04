package com.flety.cargas.viajes;

import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelar un viaje (FLY-28). Lo puede hacer cualquiera de las dos partes, solo antes de cargar.
 * Si el estado actual lo permite lo decide el viaje (patrón State): acá solo se controla que quien cancela
 * sea parte de <b>este</b> viaje (RN-T1).
 */
@Service
public class CancelarViajeService {

    private final ViajeRepository viajes;
    private final UsuarioActual usuarioActual;

    public CancelarViajeService(ViajeRepository viajes, UsuarioActual usuarioActual) {
        this.viajes = viajes;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Una sola transacción para todo lo que cambia la cancelación: si algo falla, no queda el viaje cancelado
     * con la carga sin actualizar, ni al revés.
     */
    @Transactional
    public void cancelar(Long viajeId, String motivo) {
        Viaje viaje = viajes.findById(viajeId).orElseThrow(() -> new RecursoNoEncontradoException("Viaje", viajeId));
        String sub = usuarioActual.id();
        if (!viaje.esParte(sub)) {
            throw new AccesoDenegadoException("No sos parte de este viaje");
        }
        viaje.cancelar(motivo, sub);
        // Igual que con los hitos: el viaje ya está managed, así que flush y no save (el merge de save copiaría
        // el evento nuevo sin su fecha). El flush adelanta el control de @Version: si otro pedido cambió el viaje
        // a la vez, el 409 sale acá.
        viajes.flush();
    }
}
