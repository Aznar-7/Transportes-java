package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
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
public class HabilitacionController {

    private final HabilitacionService service;

    public HabilitacionController(HabilitacionService service) {
        this.service = service;
    }

    @GetMapping("/api/transporte/unidades/{id}/habilitacion")
    public HabilitacionUnidadDTO deMiUnidad(@PathVariable Long id,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return service.deMiUnidad(id, hasta == null ? Instant.now() : hasta);
    }
}
