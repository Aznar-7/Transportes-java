package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaResponse;
import com.flety.cargas.mercado.dto.PublicarCargaRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/cargas")
public class CargaController {

    private final CargaService service;

    public CargaController(CargaService service) {
        this.service = service;
    }

    /** 201 con la carga PUBLICADA. 400 si está mal formada; 422 por RN-07 o RN-08; 503 si Georef no ubica la localidad. */
    @PostMapping
    @PreAuthorize("hasRole('DADOR')")
    public ResponseEntity<CargaResponse> publicar(@Valid @RequestBody PublicarCargaRequest pedido) {
        CargaResponse carga = service.publicar(pedido);
        return ResponseEntity.created(URI.create("/api/cargas/" + carga.id())).body(carga);
    }
}
