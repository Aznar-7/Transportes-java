package com.flety.cargas.viajes;

import com.flety.cargas.viajes.dto.DisputaDTO;
import com.flety.cargas.viajes.dto.PedidoResolucionDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El operador atiende las disputas (FLY-31). Todo bajo /api/viajes/disputas, que el Gateway reserva para OPERADOR
 * (docs/gateway/rutas.md). Spring elige este controller y no el de /api/viajes/{id} porque "disputas" es un tramo
 * literal y gana sobre una variable.
 */
@RestController
@RequestMapping("/api/viajes/disputas")
@PreAuthorize("hasRole('OPERADOR')")
public class DisputaDelOperadorController {

    private final ResolverDisputaService disputas;

    public DisputaDelOperadorController(ResolverDisputaService disputas) {
        this.disputas = disputas;
    }

    /** 200 con las disputas de ese estado, de la más vieja a la más nueva. Por defecto, las ABIERTAS. */
    @GetMapping
    public List<DisputaDTO> listar(@RequestParam(defaultValue = "ABIERTA") EstadoDisputa estado) {
        return disputas.listar(estado);
    }

    /** 200 con la disputa; 404 si no existe. */
    @GetMapping("/{id}")
    public DisputaDTO detalle(@PathVariable Long id) {
        return disputas.detalle(id);
    }

    /**
     * 200 con la disputa RESUELTA y el estado final del viaje: CONFORMADO a favor del transportista, CERRADO_CON_DISPUTA
     * en contra. 400 si falta la decisión o el fundamento; 404 si la disputa no existe; 409 si ya estaba resuelta.
     */
    @PostMapping("/{id}/resolucion")
    public DisputaDTO resolver(@PathVariable Long id, @Valid @RequestBody PedidoResolucionDTO pedido) {
        return disputas.resolver(id, pedido.aFavorDelTransportista(), pedido.resolucion());
    }
}
