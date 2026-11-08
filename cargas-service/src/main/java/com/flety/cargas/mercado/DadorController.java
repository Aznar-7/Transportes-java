package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.AltaDadorRequest;
import com.flety.cargas.mercado.dto.DadorResponse;
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

@RestController
@RequestMapping("/api/dadores")
@PreAuthorize("hasRole('DADOR')")
@Tag(name = "Dadores", description = "La empresa que publica cargas")
public class DadorController {

    private final DadorService service;

    public DadorController(DadorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta la organización del dador", description = "El dueño sale del token.")
    @ApiResponse(responseCode = "201", description = "Organización creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, por ejemplo un CUIT con el dígito verificador mal")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR")
    @ApiResponse(responseCode = "409", description = "El CUIT ya existe o el usuario ya tiene organización")
    public DadorResponse registrar(@Valid @RequestBody AltaDadorRequest pedido) {
        return service.registrar(pedido);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar la organización del dador autenticado")
    @ApiResponse(responseCode = "200", description = "La organización")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR")
    @ApiResponse(responseCode = "404", description = "Todavía no registró su organización")
    public DadorResponse miOrganizacion() {
        return service.buscarPropio();
    }
}
