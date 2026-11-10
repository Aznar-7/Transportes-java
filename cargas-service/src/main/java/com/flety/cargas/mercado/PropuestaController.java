package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.PropuestaResponse;
import com.flety.cargas.mercado.dto.ProponerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cargas")
@Tag(name = "Propuestas", description = "Proponerse para una carga, retirarse y rechazar")
public class PropuestaController {

    private final PropuestaService service;

    public PropuestaController(PropuestaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/propuestas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TRANSPORTISTA')")
    @Operation(summary = "Proponerse para una carga", description = "Aceptando el precio o con una única contraoferta (RN-03). "
            + "La propuesta queda PENDIENTE y la carga CON_PROPUESTAS.")
    @ApiResponse(responseCode = "201", description = "Propuesta creada; requiereAceptacionRiesgo avisa si aplica RN-06")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, por ejemplo una contraoferta sin precio")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA, o la unidad o el retorno son de otro")
    @ApiResponse(responseCode = "404", description = "La carga, la unidad o el retorno no existen")
    @ApiResponse(responseCode = "409", description = "La carga o el retorno ya no están abiertos, ya propuso, o la unidad tiene un viaje que se superpone (RN-15)")
    @ApiResponse(responseCode = "422", description = "RN-04 o RN-01: unidad no habilitada o incompatible")
    @ApiResponse(responseCode = "503", description = "transporte-service no responde: falla cerrada, no se crea nada")
    public PropuestaResponse proponer(@PathVariable Long id, @Valid @RequestBody ProponerRequest pedido) {
        return service.proponer(id, pedido);
    }

    @GetMapping("/{id}/propuestas")
    @PreAuthorize("hasRole('DADOR')")
    @Operation(summary = "Ver las propuestas de una carga")
    @ApiResponse(responseCode = "200", description = "Las propuestas, en orden de llegada")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR, o es la carga de otro dador")
    @ApiResponse(responseCode = "404", description = "La carga no existe")
    public List<PropuestaResponse> listar(@PathVariable Long id) {
        return service.listarDeLaCarga(id);
    }

    @DeleteMapping("/propuestas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TRANSPORTISTA')")
    @Operation(summary = "Retirar una propuesta", description = "Queda RETIRADA; no se puede volver a proponer para esa carga.")
    @ApiResponse(responseCode = "204", description = "Propuesta retirada")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA, o la propuesta es de otro")
    @ApiResponse(responseCode = "404", description = "La propuesta no existe")
    @ApiResponse(responseCode = "409", description = "Ya no está PENDIENTE")
    public void retirar(@PathVariable Long id) {
        service.retirar(id);
    }

    @PostMapping("/propuestas/{id}/rechazo")
    @PreAuthorize("hasRole('DADOR')")
    @Operation(summary = "Rechazar una propuesta", description = "Queda RECHAZADA y la carga sigue abierta para las demás.")
    @ApiResponse(responseCode = "200", description = "Propuesta rechazada")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR, o es la carga de otro dador")
    @ApiResponse(responseCode = "404", description = "La propuesta no existe")
    @ApiResponse(responseCode = "409", description = "Ya no está PENDIENTE")
    public PropuestaResponse rechazar(@PathVariable Long id) {
        return service.rechazar(id);
    }
}
