package com.flety.transporte.operador;

import com.flety.transporte.documento.DocumentoService;
import com.flety.transporte.documento.EstadoDocumento;
import com.flety.transporte.documento.dto.DocumentoResponse;
import com.flety.transporte.operador.dto.RevisionDocumentoRequest;
import com.flety.transporte.transportista.EstadoTransportista;
import com.flety.transporte.transportista.TransportistaService;
import com.flety.transporte.transportista.dto.TransportistaResponse;
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
public class OperadorController {

    private final TransportistaService transportistas;
    private final DocumentoService documentos;

    public OperadorController(TransportistaService transportistas, DocumentoService documentos) {
        this.transportistas = transportistas;
        this.documentos = documentos;
    }

    @GetMapping("/transportistas")
    public List<TransportistaResponse> listarTransportistas(@RequestParam(required = false) EstadoTransportista estado) {
        return transportistas.listar(estado);
    }

    @PatchMapping("/transportistas/{id}/verificacion")
    public TransportistaResponse verificar(@PathVariable Long id) {
        return transportistas.verificar(id);
    }

    @GetMapping("/documentos")
    public List<DocumentoResponse> listarDocumentos(@RequestParam(required = false) EstadoDocumento estado) {
        return documentos.listar(estado);
    }

    @PatchMapping("/documentos/{id}/revision")
    public DocumentoResponse revisar(@PathVariable Long id, @Valid @RequestBody RevisionDocumentoRequest pedido) {
        return documentos.revisar(id, pedido.aprobado(), pedido.motivo());
    }
}
