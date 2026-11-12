package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.transporte.retorno.RetornoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Interno", description = "Solo para cargas-service por la red de Docker; el Gateway no lo publica")
public class InternoController {

    private final HabilitacionService habilitacion;
    private final RetornoService retornos;

    public InternoController(HabilitacionService habilitacion, RetornoService retornos) {
        this.habilitacion = habilitacion;
        this.retornos = retornos;
    }

    @GetMapping("/retornos/{id}")
    @Operation(summary = "Datos de un retorno para buscar cargas de paso (flujo A)",
            description = "No verifica dueño: devuelve transportistaSub y lo controla cargas-service.")
    @ApiResponse(responseCode = "200", description = "El retorno con su ruta y los datos de la unidad")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "404", description = "El retorno no existe")
    public RetornoInternoDTO retorno(@PathVariable Long id) {
        return retornos.interno(id);
    }

    @GetMapping("/unidades/{id}/habilitacion")
    @Operation(summary = "Habilitación de una unidad hasta una fecha (flujos B y C, RN-04)",
            description = "No verifica dueño: devuelve transportistaSub y lo controla cargas-service.")
    @ApiResponse(responseCode = "200", description = "La habilitación con sus motivos")
    @ApiResponse(responseCode = "400", description = "Falta \"hasta\" o no es una fecha ISO-8601")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "404", description = "La unidad no existe")
    public HabilitacionUnidadDTO habilitacion(@PathVariable Long id,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return habilitacion.interna(id, hasta);
    }
}
