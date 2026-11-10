package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaReservadaResponse;
import com.flety.cargas.mercado.dto.CargaResponse;
import com.flety.cargas.mercado.dto.CargaVista;
import com.flety.cargas.mercado.dto.PublicarCargaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/cargas")
@Tag(name = "Cargas", description = "Publicar, consultar y dar de baja cargas")
public class CargaController {

    private final CargaService service;

    public CargaController(CargaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('DADOR')")
    @Operation(summary = "Publicar una carga", description = "Queda PUBLICADA. Las coordenadas son opcionales: si no vienen, se le piden a Georef.")
    @ApiResponse(responseCode = "201", description = "Carga publicada, con su Location")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o ventanas que no son coherentes")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR")
    @ApiResponse(responseCode = "422", description = "RN-07 o RN-08: sin organización, sin declararse empresa, o carga peligrosa o sobredimensionada")
    @ApiResponse(responseCode = "503", description = "Georef no ubica la localidad y no se mandaron coordenadas")
    public ResponseEntity<CargaResponse> publicar(@Valid @RequestBody PublicarCargaRequest pedido) {
        CargaResponse carga = service.publicar(pedido);
        return ResponseEntity.created(URI.create("/api/cargas/" + carga.id())).body(carga);
    }

    @GetMapping
    @PreAuthorize("hasRole('DADOR')")
    @Operation(summary = "Listar las cargas propias", description = "El dueño sale del token. Los filtros son opcionales; el rubro ignora mayúsculas.")
    @ApiResponse(responseCode = "200", description = "Las cargas, de la más nueva a la más vieja")
    @ApiResponse(responseCode = "400", description = "El estado no existe")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR")
    public List<CargaResponse> listar(@RequestParam(required = false) EstadoCarga estado,
                                      @RequestParam(required = false) String rubro) {
        return service.listarPropias(estado, rubro);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DADOR', 'TRANSPORTISTA')")
    @Operation(summary = "Consultar una carga", description = "RN-13: su dador la ve completa; un transportista, sin dirección, coordenadas ni datos del dador.")
    @ApiResponse(responseCode = "200", description = "La carga, con los datos que corresponden a quien mira",
            content = @Content(schema = @Schema(oneOf = {CargaResponse.class, CargaReservadaResponse.class})))
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no alcanza, o es la carga de otro dador")
    @ApiResponse(responseCode = "404", description = "La carga no existe")
    public CargaVista ver(@PathVariable Long id) {
        return service.ver(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DADOR')")
    @Operation(summary = "Dar de baja una carga", description = "Baja lógica: queda CANCELADA y sus propuestas pendientes, RECHAZADA.")
    @ApiResponse(responseCode = "204", description = "Carga cancelada")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es DADOR, o es la carga de otro dador")
    @ApiResponse(responseCode = "404", description = "La carga no existe")
    @ApiResponse(responseCode = "409", description = "Tiene un viaje en curso o ya terminó")
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }
}
