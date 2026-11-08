package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.AltaDadorRequest;
import com.flety.cargas.mercado.dto.DadorResponse;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DadorService {

    private final DadorRepository repository;
    private final UsuarioActual usuarioActual;

    public DadorService(DadorRepository repository, UsuarioActual usuarioActual) {
        this.repository = repository;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Los dos controles dan un mensaje claro en el caso común. Si dos pedidos iguales pasan a la vez, el segundo
     * choca con el índice único y flety-commons también responde 409.
     */
    @Transactional
    public DadorResponse registrar(AltaDadorRequest pedido) {
        String ownerSub = usuarioActual.id();
        if (repository.existsByOwnerSub(ownerSub)) {
            throw new ConflictoException("Tu usuario ya tiene una organización registrada");
        }
        if (repository.existsByCuit(pedido.cuit())) {
            throw new ConflictoException("Ya existe un dador con CUIT " + pedido.cuit());
        }
        Dador nuevo = new Dador(ownerSub, pedido.cuit(), pedido.razonSocial(), pedido.telefono(), pedido.declaraEmpresa());
        return DadorResponse.desde(repository.save(nuevo));
    }
}
