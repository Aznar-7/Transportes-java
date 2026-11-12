package com.flety.transporte.transportista;

import com.flety.transporte.transportista.dto.AltaTransportistaRequest;
import com.flety.transporte.transportista.dto.TransportistaResponse;
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
@RequestMapping("/api/transporte/transportistas")
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Transportistas", description = "La organización del transportista")
public class TransportistaController {

    private final TransportistaService service;

    public TransportistaController(TransportistaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta la organización del transportista",
            description = "El dueño sale del token. Nace PENDIENTE_VERIFICACION hasta que la verifique un operador.")
    @ApiResponse(responseCode = "201", description = "Organización creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, por ejemplo un CUIT con el dígito verificador mal")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA")
    @ApiResponse(responseCode = "409", description = "El CUIT ya existe o el usuario ya tiene organización")
    public TransportistaResponse registrar(@Valid @RequestBody AltaTransportistaRequest pedido) {
        return service.registrar(pedido);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar la organización del transportista autenticado")
    @ApiResponse(responseCode = "200", description = "La organización")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA")
    @ApiResponse(responseCode = "404", description = "Todavía no registró su organización")
    public TransportistaResponse miOrganizacion() {
        return service.buscarPropio();
    }
}
