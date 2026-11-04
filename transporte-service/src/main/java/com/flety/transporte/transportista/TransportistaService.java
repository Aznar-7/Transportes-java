package com.flety.transporte.transportista;

import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import com.flety.transporte.transportista.dto.AltaTransportistaRequest;
import com.flety.transporte.transportista.dto.TransportistaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportistaService {

    private final TransportistaRepository repository;
    private final UsuarioActual usuarioActual;

    public TransportistaService(TransportistaRepository repository, UsuarioActual usuarioActual) {
        this.repository = repository;
        this.usuarioActual = usuarioActual;
    }

    @Transactional
    public TransportistaResponse registrar(AltaTransportistaRequest pedido) {
        String ownerSub = usuarioActual.id();
        if (repository.existsByOwnerSub(ownerSub)) {
            throw new ConflictoException("Tu usuario ya tiene una organización registrada");
        }
        if (repository.existsByCuit(pedido.cuit())) {
            throw new ConflictoException("Ya existe un transportista con CUIT " + pedido.cuit());
        }
        Transportista nuevo = new Transportista(ownerSub, pedido.cuit(), pedido.razonSocial(), pedido.telefono());
        return TransportistaResponse.desde(repository.save(nuevo));
    }

    @Transactional(readOnly = true)
    public TransportistaResponse buscarPropio() {
        return repository.findByOwnerSub(usuarioActual.id())
                .map(TransportistaResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Todavía no registraste tu organización"));
    }
}
