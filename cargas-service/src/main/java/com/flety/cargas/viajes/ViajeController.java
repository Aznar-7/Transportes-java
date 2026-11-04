package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.PedidoCancelacionDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import com.flety.cargas.viajes.dto.ViajeResumenDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de viajes (FLY-25) y cancelación (FLY-28), las acciones que hacen las dos partes. El path es el mismo que publica el Gateway, que reenvía sin reescribirlo
 * (docs/gateway/rutas.md). El rol se vuelve a controlar acá aunque el Gateway ya lo haya hecho:
 * si alguien llega directo al servicio, sin pasar por el Gateway, igual se corta.
 */
@RestController
@RequestMapping("/api/viajes")
@PreAuthorize("hasAnyRole('DADOR', 'TRANSPORTISTA')")
public class ViajeController {

    private final ConsultaViajesService consultas;
    private final CancelarViajeService cancelacion;

    public ViajeController(ConsultaViajesService consultas, CancelarViajeService cancelacion) {
        this.consultas = consultas;
        this.cancelacion = cancelacion;
    }

    /** 200 con mis viajes (puede ser una lista vacía). */
    @GetMapping
    public List<ViajeResumenDTO> misViajes() {
        return consultas.misViajes();
    }

    /** 200 con contactos y línea de tiempo; 403 si no soy parte; 404 si no existe. */
    @GetMapping("/{id}")
    public ViajeDetalleDTO detalle(@PathVariable Long id) {
        return consultas.detalle(id);
    }

    /**
     * Cualquiera de las dos partes cancela el viaje (FLY-28). 200 con el viaje cancelado; 400 sin motivo;
     * 403 si no soy parte; 404 si no existe; 409 si ya se cargó (o está en un estado posterior).
     */
    @PostMapping("/{id}/cancelacion")
    public ViajeDetalleDTO cancelar(@PathVariable Long id, @Valid @RequestBody PedidoCancelacionDTO pedido) {
        cancelacion.cancelar(id, pedido.motivo());
        return consultas.detalle(id);
    }
}
