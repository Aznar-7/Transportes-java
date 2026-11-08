package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.PropuestaResponse;
import com.flety.cargas.mercado.dto.ProponerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargas")
public class PropuestaController {

    private final PropuestaService service;

    public PropuestaController(PropuestaService service) {
        this.service = service;
    }

    /**
     * 201 con la propuesta PENDIENTE y la carga CON_PROPUESTAS. 403 si la unidad o el retorno son de otro; 404 si algo
     * no existe; 409 si la carga está cerrada o ya propusiste; 422 si no está habilitada o es incompatible; 503 si
     * transporte-service no responde.
     */
    @PostMapping("/{id}/propuestas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRANSPORTISTA')")
    public PropuestaResponse proponer(@PathVariable Long id, @Valid @RequestBody ProponerRequest pedido) {
        return service.proponer(id, pedido);
    }
}
