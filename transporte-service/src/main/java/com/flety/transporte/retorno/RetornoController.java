package com.flety.transporte.retorno;

import com.flety.transporte.retorno.dto.PublicarRetornoRequest;
import com.flety.transporte.retorno.dto.RetornoResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transporte/retornos")
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Retornos", description = "La vuelta del camión, con su ruta, para buscar cargas de paso")
public class RetornoController {

    private final RetornoService service;

    public RetornoController(RetornoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publicar un retorno con la ruta calculada",
            description = "Sin coordenadas se piden a Georef. Si la API de rutas falla, queda la línea recta con rutaAproximada=true.")
    @ApiResponse(responseCode = "201", description = "Retorno PUBLICADO")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, por ejemplo la ventana al revés o una sola coordenada")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA o la unidad es de otro")
    @ApiResponse(responseCode = "404", description = "La unidad no existe")
    @ApiResponse(responseCode = "503", description = "Georef no ubicó la localidad: hay que mandar las coordenadas")
    public RetornoResponse publicar(@Valid @RequestBody PublicarRetornoRequest pedido) {
        return service.publicar(pedido);
    }

    @GetMapping
    @Operation(summary = "Listar los retornos propios, opcionalmente por estado")
    @ApiResponse(responseCode = "200", description = "Los retornos, por fecha de salida")
    @ApiResponse(responseCode = "400", description = "El estado no existe")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA")
    public List<RetornoResponse> listar(@RequestParam(required = false) EstadoRetorno estado) {
        return service.listarPropios(estado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un retorno propio")
    @ApiResponse(responseCode = "200", description = "El retorno")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA o el retorno es de otro")
    @ApiResponse(responseCode = "404", description = "El retorno no existe")
    public RetornoResponse ver(@PathVariable Long id) {
        return service.ver(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancelar un retorno propio", description = "Baja lógica: queda CANCELADO.")
    @ApiResponse(responseCode = "204", description = "Retorno CANCELADO")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA o el retorno es de otro")
    @ApiResponse(responseCode = "404", description = "El retorno no existe")
    @ApiResponse(responseCode = "409", description = "El retorno ya no está PUBLICADO (venció o se canceló)")
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }
}
