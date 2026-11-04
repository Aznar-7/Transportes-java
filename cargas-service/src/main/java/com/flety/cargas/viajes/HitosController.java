package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.RegistroHitoDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hitos del viaje (FLY-27): solo el transportista los registra, como publica el Gateway (docs/gateway/rutas.md).
 * Está separado de ViajeController porque ese admite DADOR o TRANSPORTISTA en toda la clase.
 */
@RestController
@RequestMapping("/api/viajes")
@PreAuthorize("hasRole('TRANSPORTISTA')")
public class HitosController {

    private final RegistrarHitoService hitos;
    private final ConsultaViajesService consultas;

    public HitosController(RegistrarHitoService hitos, ConsultaViajesService consultas) {
        this.hitos = hitos;
        this.consultas = consultas;
    }

    /**
     * 200 con el viaje actualizado. 400 si el pedido está mal armado; 403 si no soy el transportista del viaje;
     * 404 si no existe; 409 si el estado actual no permite ese hito (saltearlo, repetirlo o volver atrás).
     */
    @PostMapping("/{id}/hitos")
    public ViajeDetalleDTO registrar(@PathVariable Long id, @Valid @RequestBody RegistroHitoDTO pedido) {
        hitos.registrar(id, pedido);
        return consultas.detalle(id);
    }
}
