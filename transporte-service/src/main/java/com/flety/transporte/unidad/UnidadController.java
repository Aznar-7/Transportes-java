package com.flety.transporte.unidad;

import com.flety.transporte.unidad.dto.AltaUnidadRequest;
import com.flety.transporte.unidad.dto.UnidadResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transporte/unidades")
@PreAuthorize("hasRole('TRANSPORTISTA')")
public class UnidadController {

    private final UnidadService service;

    public UnidadController(UnidadService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadResponse registrar(@Valid @RequestBody AltaUnidadRequest pedido) {
        return service.registrar(pedido);
    }

    @GetMapping
    public List<UnidadResponse> listar() {
        return service.listarPropias();
    }
}
