package com.flety.transporte.reloj;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.Ruta;
import com.flety.transporte.documento.Documento;
import com.flety.transporte.documento.DocumentoRepository;
import com.flety.transporte.documento.DocumentoService;
import com.flety.transporte.documento.EstadoDocumento;
import com.flety.transporte.documento.TipoDocumentoRepository;
import com.flety.transporte.retorno.EstadoRetorno;
import com.flety.transporte.retorno.Retorno;
import com.flety.transporte.retorno.RetornoRepository;
import com.flety.transporte.retorno.RetornoService;
import com.flety.transporte.retorno.Ubicacion;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.transportista.TransportistaRepository;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada regla se prueba con una hora fija ("AHORA"), sin esperar al scheduler: las fechas de los datos
 * se arman antes o después de esa hora, así el test da lo mismo cualquier día que corra.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelojesTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant AYER = AHORA.minus(1, ChronoUnit.DAYS);
    private static final Instant MANANA = AHORA.plus(1, ChronoUnit.DAYS);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);

    @Autowired
    DocumentoService documentoService;

    @Autowired
    RetornoService retornoService;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    @Autowired
    TipoDocumentoRepository tipos;

    @Autowired
    DocumentoRepository documentos;

    @Autowired
    RetornoRepository retornos;

    @Autowired
    ApplicationContext contexto;

    Unidad unidad;

    @BeforeEach
    void darDeAltaLaUnidad() {
        Transportista perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
        unidad = unidades.save(new Unidad(perez, "AB123CD", TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00")));
    }

    @Test
    void unDocumentoConLaVigenciaCumplidaQuedaVencido() {
        Documento aprobadoCumplido = documento(AYER);
        aprobadoCumplido.aprobar();
        Documento enRevisionCumplido = documento(AYER);

        assertThat(documentoService.vencerCumplidos(AHORA)).isEqualTo(2);

        assertThat(aprobadoCumplido.getEstado()).isEqualTo(EstadoDocumento.VENCIDO);
        assertThat(enRevisionCumplido.getEstado()).isEqualTo(EstadoDocumento.VENCIDO);
    }

    @Test
    void unDocumentoVigenteOUnoRechazadoNoCambian() {
        Documento vigente = documento(MANANA);
        vigente.aprobar();
        Documento rechazado = documento(AYER);
        rechazado.rechazar("Foto ilegible");

        assertThat(documentoService.vencerCumplidos(AHORA)).isZero();

        assertThat(vigente.getEstado()).isEqualTo(EstadoDocumento.APROBADO);
        assertThat(rechazado.getEstado()).isEqualTo(EstadoDocumento.RECHAZADO);
    }

    @Test
    void unRetornoConLaVentanaPasadaQuedaVencido() {
        Retorno pasado = retorno(AYER);

        assertThat(retornoService.vencerPasados(AHORA)).isEqualTo(1);

        assertThat(pasado.getEstado()).isEqualTo(EstadoRetorno.VENCIDO);
    }

    @Test
    void unRetornoVigenteOUnoCanceladoNoCambian() {
        Retorno vigente = retorno(MANANA);
        Retorno cancelado = retorno(AYER);
        cancelado.cancelar();

        assertThat(retornoService.vencerPasados(AHORA)).isZero();

        assertThat(vigente.getEstado()).isEqualTo(EstadoRetorno.PUBLICADO);
        assertThat(cancelado.getEstado()).isEqualTo(EstadoRetorno.CANCELADO);
    }

    @Test
    void enLosTestsElRelojEstaApagado() {
        assertThat(contexto.getBeanNamesForType(RelojesTransporte.class)).isEmpty();
    }

    private Documento documento(Instant vigencia) {
        return documentos.save(new Documento(tipos.findAll().getFirst(), unidad, "N-1", vigencia, null));
    }

    private Retorno retorno(Instant salidaHasta) {
        List<Coordenada> extremos = List.of(ROSARIO, CORDOBA);
        return retornos.save(new Retorno(unidad, new Ubicacion("Santa Fe", "Rosario", ROSARIO),
                new Ubicacion("Córdoba", "Córdoba", CORDOBA), salidaHasta.minus(12, ChronoUnit.HOURS), salidaHasta,
                30.0, new Ruta(401.5, 290.0, extremos), false));
    }
}
