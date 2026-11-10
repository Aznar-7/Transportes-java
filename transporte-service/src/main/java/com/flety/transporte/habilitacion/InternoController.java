package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.transporte.retorno.RetornoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Contrato interno (docs/contrato-interno/transporte-interno.yml). Lo consume cargas-service por la red de Docker:
 * el Gateway no publica /internal/**. Pide un token válido de cualquier rol, porque cargas reenvía el del usuario.
 */
@RestController
@RequestMapping("/internal")
public class InternoController {

    private final HabilitacionService habilitacion;
    private final RetornoService retornos;

    public InternoController(HabilitacionService habilitacion, RetornoService retornos) {
        this.habilitacion = habilitacion;
        this.retornos = retornos;
    }

    @GetMapping("/retornos/{id}")
    public RetornoInternoDTO retorno(@PathVariable Long id) {
        return retornos.interno(id);
    }

    @GetMapping("/unidades/{id}/habilitacion")
    public HabilitacionUnidadDTO habilitacion(@PathVariable Long id,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return habilitacion.interna(id, hasta);
    }
}
