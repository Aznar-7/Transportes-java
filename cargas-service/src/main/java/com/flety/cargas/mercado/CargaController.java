package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaResponse;
import com.flety.cargas.mercado.dto.CargaVista;
import com.flety.cargas.mercado.dto.PublicarCargaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;
import java.util.List;

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

    /** Solo las propias: el dueño sale del token. Un estado que no existe da 400. */
    @GetMapping
    @PreAuthorize("hasRole('DADOR')")
    public List<CargaResponse> listar(@RequestParam(required = false) EstadoCarga estado,
                                      @RequestParam(required = false) String rubro) {
        return service.listarPropias(estado, rubro);
    }

    /** Completa para su dador y reservada para un transportista (RN-13). 403 para otro dador; 404 si no existe. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DADOR', 'TRANSPORTISTA')")
    public CargaVista ver(@PathVariable Long id) {
        return service.ver(id);
    }

    /** 204 y la carga queda CANCELADA. 403 si no es del dador; 404 si no existe; 409 si tiene viaje o ya terminó. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DADOR')")
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }
}
