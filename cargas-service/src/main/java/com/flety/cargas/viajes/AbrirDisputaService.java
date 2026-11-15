package com.flety.cargas.viajes;

import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El dador reclama sobre la entrega (FLY-31). El rol DADOR ya lo controlaron el Gateway y el controller; acá se
 * controla que sea el dador de <b>este</b> viaje (RN-T1). Si el estado y la ventana lo permiten lo decide el viaje.
 */
@Service
public class AbrirDisputaService {

    private final ViajeRepository viajes;
    private final DisputaRepository disputas;
    private final UsuarioActual usuarioActual;

    public AbrirDisputaService(ViajeRepository viajes, DisputaRepository disputas, UsuarioActual usuarioActual) {
        this.viajes = viajes;
        this.disputas = disputas;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Una transacción para el viaje y la disputa: o el viaje queda EN_DISPUTA con su disputa guardada, o ninguno de
     * los dos cambia.
     */
    @Transactional
    public void abrir(Long viajeId, String motivo, String descripcion) {
        Viaje viaje = viajes.findById(viajeId).orElseThrow(() -> new RecursoNoEncontradoException("Viaje", viajeId));
        String sub = usuarioActual.id();
        if (!viaje.getDadorSub().equals(sub)) {
            throw new AccesoDenegadoException("No sos el dador de este viaje");
        }
        disputas.save(viaje.disputar(motivo, descripcion, sub));
        // flush y no save del viaje, como en los demás cambios de estado: el viaje ya está managed. Si el Reloj lo
        // conformó en el mismo momento, el @Version lo detecta acá y sale 409.
        viajes.flush();
    }
}
