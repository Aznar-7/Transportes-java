package com.flety.transporte.transportista;

import com.flety.transporte.transportista.dto.AltaTransportistaRequest;
import com.flety.transporte.transportista.dto.TransportistaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transporte/transportistas")
public class TransportistaController {

    private final TransportistaService service;

    public TransportistaController(TransportistaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransportistaResponse registrar(@Valid @RequestBody AltaTransportistaRequest pedido) {
        return service.registrar(pedido);
    }

    @GetMapping("/me")
    public TransportistaResponse miOrganizacion() {
        return service.buscarPropio();
    }
}
