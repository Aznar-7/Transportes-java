package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.AceptacionPropuestaDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Aceptar una propuesta (FLY-26). Vive bajo /api/cargas porque así lo publica el Gateway (docs/gateway/rutas.md),
 * pero es de viajes: aceptar una propuesta es lo que crea el viaje.
 */
@RestController
@RequestMapping("/api/cargas/propuestas")
@PreAuthorize("hasRole('DADOR')")
public class AceptacionController {

    private final AceptarPropuestaService aceptacion;
    private final ConsultaViajesService consultas;

    public AceptacionController(AceptarPropuestaService aceptacion, ConsultaViajesService consultas) {
        this.aceptacion = aceptacion;
        this.consultas = consultas;
    }

    /**
     * 201 con el viaje creado y su Location. 403 si la carga no es del dador; 404 si la propuesta no existe;
     * 409 si la propuesta o la carga ya no están abiertas; 422 si falta aceptar el riesgo (RN-06).
     */
    @PostMapping("/{id}/aceptacion")
    public ResponseEntity<ViajeDetalleDTO> aceptar(@PathVariable Long id,
                                                   @RequestBody(required = false) AceptacionPropuestaDTO cuerpo) {
        Long viajeId = aceptacion.aceptar(id, cuerpo != null && cuerpo.aceptoRiesgo());
        return ResponseEntity.created(URI.create("/api/viajes/" + viajeId)).body(consultas.detalle(viajeId));
    }
}
