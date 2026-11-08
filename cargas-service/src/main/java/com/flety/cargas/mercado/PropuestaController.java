package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.PropuestaResponse;
import com.flety.cargas.mercado.dto.ProponerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** 403 si la carga no es del dador; 404 si no existe. */
    @GetMapping("/{id}/propuestas")
    @PreAuthorize("hasRole('DADOR')")
    public List<PropuestaResponse> listar(@PathVariable Long id) {
        return service.listarDeLaCarga(id);
    }

    /** 204 y la propuesta queda RETIRADA. 403 si no es del transportista; 404 si no existe; 409 si ya no está PENDIENTE. */
    @DeleteMapping("/propuestas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TRANSPORTISTA')")
    public void retirar(@PathVariable Long id) {
        service.retirar(id);
    }

    /** 200 y la propuesta queda RECHAZADA. 403 si la carga no es del dador; 404 si no existe; 409 si ya no está PENDIENTE. */
    @PostMapping("/propuestas/{id}/rechazo")
    @PreAuthorize("hasRole('DADOR')")
    public PropuestaResponse rechazar(@PathVariable Long id) {
        return service.rechazar(id);
    }
}
