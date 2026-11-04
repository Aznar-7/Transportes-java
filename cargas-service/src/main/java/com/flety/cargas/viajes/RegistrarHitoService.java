package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.RegistroHitoDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El transportista registra un hito de su viaje (FLY-27). El rol TRANSPORTISTA ya lo controlaron el Gateway y el
 * controller; acá se controla que sea el transportista de <b>este</b> viaje (RN-T1). Si el estado permite el hito
 * lo decide el propio viaje, con el patrón State.
 */
@Service
public class RegistrarHitoService {

    private final ViajeRepository viajes;
    private final UsuarioActual usuarioActual;

    public RegistrarHitoService(ViajeRepository viajes, UsuarioActual usuarioActual) {
        this.viajes = viajes;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Todo en una transacción: si el estado no permite el hito, la excepción la deshace y no queda ni el cambio
     * de estado ni el evento.
     */
    @Transactional
    public void registrar(Long viajeId, RegistroHitoDTO pedido) {
        Viaje viaje = viajes.findById(viajeId).orElseThrow(() -> new RecursoNoEncontradoException("Viaje", viajeId));
        String sub = usuarioActual.id();
        if (!viaje.getTransportistaSub().equals(sub)) {
            throw new AccesoDenegadoException("No sos el transportista de este viaje");
        }
        // Este switch solo traduce el pedido de la API a la acción del dominio. Si la acción vale en el estado
        // actual no se decide acá sino en la clase del estado: este switch no cambia cuando cambian las reglas.
        switch (pedido.hito()) {
            case CARGADO -> viaje.cargar(sub);
            case EN_TRANSITO -> viaje.iniciarTransito(sub);
            case ENTREGADO -> viaje.registrarEntrega(
                    pedido.comprobante() == null ? null : pedido.comprobante().aDatos(), sub);
        }
        // El viaje ya está managed (se leyó en esta transacción): no hace falta save, Hibernate detecta el cambio.
        // Y no hay que usar save: haría un merge, que copia el EventoViaje nuevo sin su fecha y rompe el INSERT.
        // flush manda el UPDATE ahora: si otro pedido cambió el mismo viaje a la vez, el @Version lo detecta acá
        // y se responde 409, en vez de fallar recién en el commit.
        viajes.flush();
    }
}
