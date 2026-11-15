package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conformidad de la entrega (FLY-30): solo el dador, como publica el Gateway (docs/gateway/rutas.md).
 * Está separado de ViajeController porque ese admite DADOR o TRANSPORTISTA en toda la clase.
 */
@RestController
@RequestMapping("/api/viajes")
@PreAuthorize("hasRole('DADOR')")
public class ConformidadController {

    private final ConformarViajeService conformidad;
    private final ConsultaViajesService consultas;

    public ConformidadController(ConformarViajeService conformidad, ConsultaViajesService consultas) {
        this.conformidad = conformidad;
        this.consultas = consultas;
    }

    /**
     * 200 con el viaje CONFORMADO. 403 si no soy el dador del viaje; 404 si no existe; 409 si el viaje no está
     * ENTREGADO o si la ventana de 48 h ya venció (la conformidad la da el sistema).
     */
    @PostMapping("/{id}/conformidad")
    public ViajeDetalleDTO conformar(@PathVariable Long id) {
        conformidad.conformar(id);
        return consultas.detalle(id);
    }
}
