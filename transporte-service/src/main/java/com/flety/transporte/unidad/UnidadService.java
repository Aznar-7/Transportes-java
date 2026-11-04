package com.flety.transporte.unidad;

import com.flety.commons.excepciones.ConflictoException;
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
}
