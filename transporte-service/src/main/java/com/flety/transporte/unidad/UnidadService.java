package com.flety.transporte.unidad;

import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.transportista.TransportistaService;
import com.flety.transporte.unidad.dto.AltaUnidadRequest;
import com.flety.transporte.unidad.dto.UnidadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnidadService {

    private final UnidadRepository repository;
    private final TransportistaService transportistas;
    private final UsuarioActual usuarioActual;

    public UnidadService(UnidadRepository repository, TransportistaService transportistas, UsuarioActual usuarioActual) {
        this.repository = repository;
        this.transportistas = transportistas;
        this.usuarioActual = usuarioActual;
    }

    @Transactional
    public UnidadResponse registrar(AltaUnidadRequest pedido) {
        Transportista transportista = transportistas.propio();
        if (repository.existsByDominio(pedido.dominio())) {
            throw new ConflictoException("Ya existe una unidad con dominio " + pedido.dominio());
        }
        Unidad nueva = new Unidad(transportista, pedido.dominio(), pedido.tipoCarroceria(),
                pedido.capacidadToneladas(), pedido.capacidadM3());
        return UnidadResponse.desde(repository.save(nueva));
    }

    @Transactional(readOnly = true)
    public List<UnidadResponse> listarPropias() {
        return repository.findByTransportistaOwnerSubOrderByDominio(usuarioActual.id()).stream()
                .map(UnidadResponse::desde)
                .toList();
    }

    /** La unidad si es del usuario actual (RN-T1): 404 si no existe, 403 si es de otro transportista. */
    @Transactional(readOnly = true)
    public Unidad propia(Long id) {
        Unidad unidad = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Unidad", id));
        if (!unidad.getTransportista().getOwnerSub().equals(usuarioActual.id())) {
            throw new AccesoDenegadoException("La unidad no es tuya");
        }
        return unidad;
    }
}
