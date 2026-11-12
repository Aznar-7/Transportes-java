package com.flety.transporte.operador;

import com.flety.transporte.documento.DocumentoService;
import com.flety.transporte.documento.EstadoDocumento;
import com.flety.transporte.documento.dto.DocumentoResponse;
import com.flety.transporte.operador.dto.RevisionDocumentoRequest;
import com.flety.transporte.transportista.EstadoTransportista;
import com.flety.transporte.transportista.TransportistaService;
import com.flety.transporte.transportista.dto.TransportistaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transporte/operador")
@PreAuthorize("hasRole('OPERADOR')")
@Tag(name = "Operador", description = "Verificación de transportistas y revisión de documentos")
public class OperadorController {

    private final TransportistaService transportistas;
    private final DocumentoService documentos;

    public OperadorController(TransportistaService transportistas, DocumentoService documentos) {
        this.transportistas = transportistas;
        this.documentos = documentos;
    }

    @GetMapping("/transportistas")
    @Operation(summary = "Listar transportistas, opcionalmente por estado")
    @ApiResponse(responseCode = "200", description = "Los transportistas")
    @ApiResponse(responseCode = "400", description = "El estado no existe")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es OPERADOR")
    public List<TransportistaResponse> listarTransportistas(@RequestParam(required = false) EstadoTransportista estado) {
        return transportistas.listar(estado);
    }

    @PatchMapping("/transportistas/{id}/verificacion")
    @Operation(summary = "Verificar a un transportista pendiente")
    @ApiResponse(responseCode = "200", description = "Queda VERIFICADO")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es OPERADOR")
    @ApiResponse(responseCode = "404", description = "El transportista no existe")
    @ApiResponse(responseCode = "409", description = "Ya estaba verificado")
    public TransportistaResponse verificar(@PathVariable Long id) {
        return transportistas.verificar(id);
    }

    @GetMapping("/documentos")
    @Operation(summary = "Listar documentos, opcionalmente por estado")
    @ApiResponse(responseCode = "200", description = "Los documentos")
    @ApiResponse(responseCode = "400", description = "El estado no existe")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es OPERADOR")
    public List<DocumentoResponse> listarDocumentos(@RequestParam(required = false) EstadoDocumento estado) {
        return documentos.listar(estado);
    }

    @PatchMapping("/documentos/{id}/revision")
    @Operation(summary = "Aprobar o rechazar un documento en revisión", description = "Un rechazo necesita motivo.")
    @ApiResponse(responseCode = "200", description = "Queda APROBADO o RECHAZADO")
    @ApiResponse(responseCode = "400", description = "Falta \"aprobado\" o es un rechazo sin motivo")
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido")
    @ApiResponse(responseCode = "403", description = "El rol no es OPERADOR")
    @ApiResponse(responseCode = "404", description = "El documento no existe")
    @ApiResponse(responseCode = "409", description = "El documento no está EN_REVISION")
    public DocumentoResponse revisar(@PathVariable Long id, @Valid @RequestBody RevisionDocumentoRequest pedido) {
        return documentos.revisar(id, pedido.aprobado(), pedido.motivo());
    }
}
