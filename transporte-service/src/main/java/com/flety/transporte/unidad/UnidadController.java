package com.flety.transporte.unidad;

import com.flety.transporte.unidad.dto.AltaUnidadRequest;
import com.flety.transporte.unidad.dto.UnidadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transporte/unidades")
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Unidades", description = "Los camiones del transportista")
public class UnidadController {

    private final UnidadService service;

    public UnidadController(UnidadService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una unidad de la organización propia")
    @ApiResponse(responseCode = "201", description = "Unidad creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, por ejemplo una patente con otro formato")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA")
    @ApiResponse(responseCode = "404", description = "Todavía no registró su organización")
    @ApiResponse(responseCode = "409", description = "Ya existe una unidad con ese dominio")
    public UnidadResponse registrar(@Valid @RequestBody AltaUnidadRequest pedido) {
        return service.registrar(pedido);
    }

    @GetMapping
    @Operation(summary = "Listar las unidades propias, por dominio")
    @ApiResponse(responseCode = "200", description = "Las unidades (vacío si no tiene)")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA")
    public List<UnidadResponse> listar() {
        return service.listarPropias();
    }
}
