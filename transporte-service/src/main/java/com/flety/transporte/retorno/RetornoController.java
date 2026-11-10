package com.flety.transporte.retorno;

import com.flety.transporte.retorno.dto.PublicarRetornoRequest;
import com.flety.transporte.retorno.dto.RetornoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transporte/retornos")
@PreAuthorize("hasRole('TRANSPORTISTA')")
public class RetornoController {

    private final RetornoService service;

    public RetornoController(RetornoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RetornoResponse publicar(@Valid @RequestBody PublicarRetornoRequest pedido) {
        return service.publicar(pedido);
    }

    @GetMapping
    public List<RetornoResponse> listar(@RequestParam(required = false) EstadoRetorno estado) {
        return service.listarPropios(estado);
    }

    @GetMapping("/{id}")
    public RetornoResponse ver(@PathVariable Long id) {
        return service.ver(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }
}
