package com.flety.transporte.documento;

import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import com.flety.transporte.documento.dto.AltaDocumentoRequest;
import com.flety.transporte.documento.dto.DocumentoResponse;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentoService {

    private final DocumentoRepository repository;
    private final TipoDocumentoRepository tipos;
    private final UnidadService unidades;

    public DocumentoService(DocumentoRepository repository, TipoDocumentoRepository tipos, UnidadService unidades) {
        this.repository = repository;
        this.tipos = tipos;
        this.unidades = unidades;
    }

    @Transactional
    public DocumentoResponse cargar(Long unidadId, AltaDocumentoRequest pedido) {
        Unidad unidad = unidades.propia(unidadId);
        TipoDocumento tipo = tipos.findById(pedido.tipoDocumentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de documento", pedido.tipoDocumentoId()));
        if (tipo.getAplicaA() != AplicaA.UNIDAD) {
            throw new ReglaNegocioException(tipo.getNombre() + " no se carga en una unidad");
        }
        if (tipo.isVence() && pedido.vigencia() == null) {
            throw new ReglaNegocioException(tipo.getNombre() + " necesita fecha de vigencia");
        }
        if (tipo.isRequiereSumaAsegurada() && pedido.sumaAsegurada() == null) {
            throw new ReglaNegocioException(tipo.getNombre() + " necesita la suma asegurada");
        }
        Documento nuevo = new Documento(tipo, unidad, pedido.numero(), pedido.vigencia(), pedido.sumaAsegurada());
        return DocumentoResponse.desde(repository.save(nuevo));
    }
}
