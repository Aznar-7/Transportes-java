package com.flety.transporte.habilitacion;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.transporte.documento.AplicaA;
import com.flety.transporte.documento.Documento;
import com.flety.transporte.documento.DocumentoRepository;
import com.flety.transporte.documento.EstadoDocumento;
import com.flety.transporte.documento.TipoDocumento;
import com.flety.transporte.documento.TipoDocumentoRepository;
import com.flety.transporte.transportista.EstadoTransportista;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadRepository;
import com.flety.transporte.unidad.UnidadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RN-04 en un solo lugar: el endpoint del dueño y el interno que consulta cargas-service usan este mismo cálculo.
 * Una unidad está habilitada hasta una fecha si su transportista está VERIFICADO y cada documento obligatorio
 * de la unidad tiene uno APROBADO y vigente hasta esa fecha.
 */
@Service
public class HabilitacionService {

    private final UnidadRepository unidades;
    private final UnidadService unidadService;
    private final DocumentoRepository documentos;
    private final TipoDocumentoRepository tipos;

    public HabilitacionService(UnidadRepository unidades, UnidadService unidadService,
                               DocumentoRepository documentos, TipoDocumentoRepository tipos) {
        this.unidades = unidades;
        this.unidadService = unidadService;
        this.documentos = documentos;
        this.tipos = tipos;
    }

    /** Para el dueño: 404 si no existe y 403 si es de otro (RN-T1). */
    @Transactional(readOnly = true)
    public HabilitacionUnidadDTO deMiUnidad(Long unidadId, Instant hasta) {
        return evaluar(unidadService.propia(unidadId), hasta);
    }

    /** Para cargas-service: no verifica dueño, lo hace cargas con el transportistaSub de la respuesta. */
    @Transactional(readOnly = true)
    public HabilitacionUnidadDTO interna(Long unidadId, Instant hasta) {
        Unidad unidad = unidades.findById(unidadId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Unidad", unidadId));
        return evaluar(unidad, hasta);
    }

    private HabilitacionUnidadDTO evaluar(Unidad unidad, Instant hasta) {
        Transportista transportista = unidad.getTransportista();
        List<Documento> aprobados = documentos.findByUnidadIdAndEstado(unidad.getId(), EstadoDocumento.APROBADO);
        List<String> motivos = new ArrayList<>();

        if (transportista.getEstado() != EstadoTransportista.VERIFICADO) {
            motivos.add("El transportista no está VERIFICADO");
        }
        for (TipoDocumento tipo : tipos.findByAplicaAAndObligatorioTrue(AplicaA.UNIDAD)) {
            List<Documento> deEseTipo = aprobados.stream().filter(d -> d.getTipo().getId().equals(tipo.getId())).toList();
            if (deEseTipo.isEmpty()) {
                motivos.add("Falta el documento obligatorio " + tipo.getNombre());
            } else if (deEseTipo.stream().noneMatch(d -> vigenteHasta(d, hasta))) {
                Instant ultimaVigencia = deEseTipo.stream().map(Documento::getVigencia).max(Comparator.naturalOrder()).orElseThrow();
                motivos.add(tipo.getNombre() + " vence el " + fecha(ultimaVigencia) + ", antes del " + fecha(hasta));
            }
        }

        BigDecimal sumaAseguradaCarga = aprobados.stream()
                .filter(d -> d.getTipo().isRequiereSumaAsegurada() && vigenteHasta(d, hasta))
                .map(Documento::getSumaAsegurada)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new HabilitacionUnidadDTO(unidad.getId(), motivos.isEmpty(), motivos, unidad.getDominio(),
                unidad.getTipoCarroceria(), unidad.getCapacidadToneladas(), unidad.getCapacidadM3(), sumaAseguradaCarga,
                transportista.getId(), transportista.getRazonSocial(), transportista.getTelefono(),
                transportista.getOwnerSub());
    }

    /** Un documento que no vence (vigencia null) sirve siempre; si vence, tiene que llegar hasta la fecha pedida. */
    private static boolean vigenteHasta(Documento documento, Instant hasta) {
        return documento.getVigencia() == null || !documento.getVigencia().isBefore(hasta);
    }

    private static String fecha(Instant instante) {
        return instante.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
}
