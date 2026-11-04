package com.flety.transporte.documento;

import com.flety.transporte.documento.dto.AltaDocumentoRequest;
import com.flety.transporte.documento.dto.DocumentoResponse;
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
public class DocumentoController {

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse cargar(@PathVariable Long unidadId, @Valid @RequestBody AltaDocumentoRequest pedido) {
        return service.cargar(unidadId, pedido);
    }
}
