package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaDePasoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cargas/de-paso")
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Búsqueda", description = "Las cargas que le quedan de paso a un retorno")
public class BusquedaCargasController {

    private final BusquedaCargasService service;

    public BusquedaCargasController(BusquedaCargasService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Buscar cargas de paso", description = "RN-01 y RN-02: compatibles con el retorno, por desvío y después por precio por km. "
            + "Si la API de rutas no responde, siguen en línea recta y salen con aproximado = true.")
    @ApiResponse(responseCode = "200", description = "Las cargas, en orden; puede ser una lista vacía")
    @ApiResponse(responseCode = "400", description = "Falta el retornoId")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA, o el retorno es de otro")
    @ApiResponse(responseCode = "404", description = "El retorno no existe")
    @ApiResponse(responseCode = "409", description = "El retorno ya no está publicado")
    @ApiResponse(responseCode = "503", description = "transporte-service no responde: falla cerrada")
    public List<CargaDePasoResponse> buscar(@RequestParam Long retornoId) {
        return service.buscar(retornoId);
    }
}
