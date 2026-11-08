package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaDePasoResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cargas/de-paso")
@PreAuthorize("hasRole('TRANSPORTISTA')")
public class BusquedaCargasController {

    private final BusquedaCargasService service;

    public BusquedaCargasController(BusquedaCargasService service) {
        this.service = service;
    }

    /** 403 si el retorno es de otro; 404 si no existe; 409 si ya no está publicado; 503 si transporte no responde. */
    @GetMapping
    public List<CargaDePasoResponse> buscar(@RequestParam Long retornoId) {
        return service.buscar(retornoId);
    }
}
