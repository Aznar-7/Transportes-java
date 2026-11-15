package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.DisputaDTO;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * La bandeja del operador y la resolución de disputas (FLY-31). El rol OPERADOR ya lo controlaron el Gateway y el
 * controller; un operador no es parte del viaje, así que no hay control de dueño: puede ver y resolver cualquiera.
 */
@Service
public class ResolverDisputaService {

    private final DisputaRepository disputas;
    private final ViajeRepository viajes;
    private final UsuarioActual usuarioActual;

    public ResolverDisputaService(DisputaRepository disputas, ViajeRepository viajes, UsuarioActual usuarioActual) {
        this.disputas = disputas;
        this.viajes = viajes;
        this.usuarioActual = usuarioActual;
    }

    @Transactional(readOnly = true)
    public List<DisputaDTO> listar(EstadoDisputa estado) {
        return disputas.findByEstadoOrderByAbiertaEnAsc(estado).stream().map(DisputaDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public DisputaDTO detalle(Long id) {
        return DisputaDTO.desde(buscar(id));
    }

    /**
     * Una transacción para el viaje, su carga y la disputa: o queda todo resuelto, o nada cambia.
     * <p>
     * Primero el viaje: si ya no está EN_DISPUTA (otro operador la resolvió) sale 409 antes de tocar la disputa.
     * Dos operadores a la vez sobre la misma disputa los frena el @Version del viaje, que los dos modifican.
     */
    @Transactional
    public DisputaDTO resolver(Long id, boolean aFavorDelTransportista, String resolucion) {
        Disputa disputa = buscar(id);
        String operador = usuarioActual.id();
        disputa.getViaje().resolverDisputa(aFavorDelTransportista, resolucion, operador);
        disputa.resolver(aFavorDelTransportista, resolucion, operador);
        viajes.flush();
        return DisputaDTO.desde(disputa);
    }

    private Disputa buscar(Long id) {
        return disputas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Disputa", id));
    }
}
