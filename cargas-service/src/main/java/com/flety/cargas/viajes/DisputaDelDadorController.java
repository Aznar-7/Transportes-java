package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.PedidoDisputaDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * El dador abre una disputa sobre la entrega (FLY-31), como publica el Gateway (docs/gateway/rutas.md).
 * La resuelve un operador en DisputaDelOperadorController.
 */
@RestController
@RequestMapping("/api/viajes")
@PreAuthorize("hasRole('DADOR')")
public class DisputaDelDadorController {

    private final AbrirDisputaService disputas;
    private final ConsultaViajesService consultas;

    public DisputaDelDadorController(AbrirDisputaService disputas, ConsultaViajesService consultas) {
        this.disputas = disputas;
        this.consultas = consultas;
    }

    /**
     * 201 con el viaje EN_DISPUTA y su disputa, y Location del viaje: la disputa se sigue desde el detalle del viaje.
     * 400 si falta el motivo o la descripción; 403 si no soy el dador del viaje; 404 si no existe; 409 si el viaje no
     * está ENTREGADO o la ventana de 48 h ya venció.
     */
    @PostMapping("/{id}/disputas")
    public ResponseEntity<ViajeDetalleDTO> abrir(@PathVariable Long id, @Valid @RequestBody PedidoDisputaDTO pedido) {
        disputas.abrir(id, pedido.motivo(), pedido.descripcion());
        return ResponseEntity.created(URI.create("/api/viajes/" + id)).body(consultas.detalle(id));
    }
}
