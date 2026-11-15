package com.flety.cargas.viajes;

import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El dador da conformidad a la entrega (FLY-30). El rol DADOR ya lo controlaron el Gateway y el controller; acá se
 * controla que sea el dador de <b>este</b> viaje (RN-T1). Si el estado y la ventana lo permiten lo decide el viaje
 * (patrón State): es la misma transición que usa el Reloj cuando conforma solo.
 */
@Service
public class ConformarViajeService {

    private final ViajeRepository viajes;
    private final UsuarioActual usuarioActual;

    public ConformarViajeService(ViajeRepository viajes, UsuarioActual usuarioActual) {
        this.viajes = viajes;
        this.usuarioActual = usuarioActual;
    }

    /** Una transacción para el viaje y su carga: o quedan CONFORMADO y CERRADA, o ninguno cambia. */
    @Transactional
    public void conformar(Long viajeId) {
        Viaje viaje = viajes.findById(viajeId).orElseThrow(() -> new RecursoNoEncontradoException("Viaje", viajeId));
        String sub = usuarioActual.id();
        if (!viaje.getDadorSub().equals(sub)) {
            throw new AccesoDenegadoException("No sos el dador de este viaje");
        }
        viaje.conformar(ConformadoPor.DADOR, sub);
        // Igual que en hitos y cancelación: flush y no save, porque save haría un merge que copia el evento nuevo
        // sin su fecha. El flush adelanta el control de @Version: si el Reloj conformó este viaje a la vez, sale 409.
        viajes.flush();
    }
}
