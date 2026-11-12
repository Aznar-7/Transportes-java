package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Para que el transportista vea qué le falta para operar. Sin "hasta", se calcula para este momento. */
@RestController
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Unidades")
public class HabilitacionController {

    private final HabilitacionService service;

    public HabilitacionController(HabilitacionService service) {
        this.service = service;
    }

    @GetMapping("/api/transporte/unidades/{id}/habilitacion")
    @Operation(summary = "Ver si una unidad propia puede proponer y qué le falta (RN-04)",
            description = "Sin \"hasta\" se calcula para este momento. Los motivos dicen qué documento falta o vence.")
    @ApiResponse(responseCode = "200", description = "La habilitación con sus motivos")
    @ApiResponse(responseCode = "400", description = "\"hasta\" no es una fecha ISO-8601")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA o la unidad es de otro")
    @ApiResponse(responseCode = "404", description = "La unidad no existe")
    public HabilitacionUnidadDTO deMiUnidad(@PathVariable Long id,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return service.deMiUnidad(id, hasta == null ? Instant.now() : hasta);
    }
}
