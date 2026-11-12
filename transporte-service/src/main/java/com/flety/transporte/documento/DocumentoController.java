package com.flety.transporte.documento;

import com.flety.transporte.documento.dto.AltaDocumentoRequest;
import com.flety.transporte.documento.dto.DocumentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transporte/unidades/{unidadId}/documentos")
@PreAuthorize("hasRole('TRANSPORTISTA')")
@Tag(name = "Documentos", description = "La documentación de cada unidad, que verifica un operador")
public class DocumentoController {

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cargar un documento de una unidad propia",
            description = "Queda EN_REVISION. Los tipos están en data.sql; la póliza de carga pide la suma asegurada (RN-06).")
    @ApiResponse(responseCode = "201", description = "Documento cargado en EN_REVISION")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o el documento ya está vencido")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es TRANSPORTISTA o la unidad es de otro")
    @ApiResponse(responseCode = "404", description = "No existe la unidad o el tipo de documento")
    @ApiResponse(responseCode = "422", description = "El tipo no se carga en una unidad, o le falta la vigencia o la suma asegurada")
    public DocumentoResponse cargar(@PathVariable Long unidadId, @Valid @RequestBody AltaDocumentoRequest pedido) {
        return service.cargar(unidadId, pedido);
    }
}
