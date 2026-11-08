package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.AltaDadorRequest;
import com.flety.cargas.mercado.dto.DadorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dadores")
@PreAuthorize("hasRole('DADOR')")
public class DadorController {

    private final DadorService service;

    public DadorController(DadorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DadorResponse registrar(@Valid @RequestBody AltaDadorRequest pedido) {
        return service.registrar(pedido);
    }
}
